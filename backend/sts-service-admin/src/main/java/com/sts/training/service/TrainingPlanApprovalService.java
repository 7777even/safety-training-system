package com.sts.training.service;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sts.admin.entity.SysUser;
import com.sts.admin.mapper.*;
import com.sts.admin.tenant.TenantContext;
import com.sts.common.exception.BusinessException;
import com.sts.training.entity.*;
import com.sts.training.mapper.*;
import com.sts.training.vo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
/** 单级审批和所有草稿写入共用计划行锁，避免提交与编辑穿透。 */
@Service @RequiredArgsConstructor public class TrainingPlanApprovalService {
  /** 计划版本及行锁。 */ private final TrainingPlanMapper plans;
  /** 历次提交。 */ private final TrainingPlanApprovalMapper approvals;
  /** 当前机构人员。 */ private final SysUserMapper users;
  /** 现有RBAC授权。 */ private final SysMenuMapper menus;
  /** 所有审批敏感写入先锁定同一计划，调用方必须处于事务。 */
  public TrainingPlan lock(Long id) {
    var p=plans.lockForApproval(id,TenantContext.requireTenantId());
    if(p==null || !Objects.equals(p.getOrganizationId(),TenantContext.requireTenantId())) throw error(8101,"培训计划不存在");
    return p;
  }
  /** 待审批草稿禁止任何修改；批准后修改由版本自动失效。 */
  public TrainingPlan lockForEdit(Long id) {
    var p=lock(id); var r=latest(id);
    if(r!=null && "PENDING".equals(r.getApprovalStatus())) throw error(8331,"待审批计划禁止编辑");
    return p;
  }
  /** 新发布只接受当前版本已批准的计划，历史已发布计划不补造审批。 */
  public void requireApproved(TrainingPlan p) {
    var r=latest(p.getTrainingPlanId());
    if(r==null || !"APPROVED".equals(r.getApprovalStatus()) || !Objects.equals(r.getPlanVersion(),p.getVersion()))
      throw error(8332,"当前计划版本尚未审批通过，请提交审批");
  }
  /** 当前计划完整历史，按最新提交优先。 */
  public List<TrainingPlanApprovalVO> history(Long id) {
    var p=plans.selectById(id);
    if(p==null || !Objects.equals(p.getOrganizationId(),TenantContext.requireTenantId())) throw error(8101,"培训计划不存在");
    return records(id).stream().map(r->{var v=new TrainingPlanApprovalVO();BeanUtils.copyProperties(r,v);return v;}).toList();
  }
  /** 启用且真实持有审批权限的当前机构人员，不能选择自己。 */
  public List<TrainingApproverVO> approvers(Long actor) {
    return users.selectList(new LambdaQueryWrapper<SysUser>().eq(SysUser::getOrganizationId,TenantContext.requireTenantId()).eq(SysUser::getStatus,1))
      .stream().filter(u->!Objects.equals(actor,u.getUserId()) && canApprove(u)).map(u->new TrainingApproverVO(u.getUserId(),u.getUserName())).toList();
  }
  /** 草稿提交；每次重提创建新记录，保留驳回意见。 */
  @Transactional public void submit(Long id,Integer version,Long approver,Long actor) {
    var p=lockForEdit(id);
    if(!"DRAFT".equals(p.getPublishStatus())) throw error(8333,"仅草稿计划可提交首次审批");
    if(!Objects.equals(version,p.getVersion())) throw error(8104,"培训计划版本冲突");
    requireApprover(actor,approver);
    var r=new TrainingPlanApproval();r.setOrganizationId(TenantContext.requireTenantId());r.setTrainingPlanId(id);r.setPlanVersion(version);
    r.setSubmitterUserId(actor);r.setApproverUserId(approver);r.setApprovalStatus("PENDING");approvals.insert(r);
  }
  /** 仅指定审批人可决定，并重新核验权限、状态和版本。 */
  @Transactional public void decide(Long id,boolean approved,String comment,Long actor) {
    var p=lock(id);var r=latest(id);
    if(r==null || !"PENDING".equals(r.getApprovalStatus())) throw error(8336,"没有待审批申请");
    if(!Objects.equals(actor,r.getApproverUserId()) || Objects.equals(actor,r.getSubmitterUserId())) throw error(8337,"仅指定审批人可审批");
    if(!Objects.equals(r.getPlanVersion(),p.getVersion()) || !"DRAFT".equals(p.getPublishStatus())) throw error(8104,"培训计划版本或状态已变化");
    if(!canApprove(users.selectById(actor))) throw error(8335,"当前人员没有审批权限");
    if(!approved && (comment==null || comment.isBlank())) throw error(8338,"驳回时请填写审批意见");
    r.setApprovalStatus(approved?"APPROVED":"REJECTED");r.setDecisionComment(comment);
    if(approvals.updateById(r)==0) throw error(8104,"审批版本冲突");
  }
  /** 首次审批和变更审批共用同一候选资格校验。 */
  public void requireApprover(Long submitter,Long approver) {
    if(Objects.equals(submitter,approver)) throw error(8334,"审批人与提交人必须不同");
    if(!canApprove(users.selectById(approver))) throw error(8335,"所选人员未启用、不属于当前机构或没有审批权限");
  }
  /** 负责人必须是当前机构启用人员，空值允许解除负责人。 */
  public void requireManager(Long id) {
    if(id==null)return;var u=users.selectById(id);
    if(u==null || !Objects.equals(u.getOrganizationId(),TenantContext.requireTenantId()) || !Integer.valueOf(1).equals(u.getStatus()))throw error(8352,"负责人不存在、已停用或不属于当前机构");
  }
  private boolean canApprove(SysUser u) {
    return u!=null && Objects.equals(u.getOrganizationId(),TenantContext.requireTenantId()) && Integer.valueOf(1).equals(u.getStatus())
      && menus.selectPermsByUserId(u.getUserId()).stream().anyMatch(s->"training:plan:approve".equals(s)||"*".equals(s));
  }
  private List<TrainingPlanApproval> records(Long id) { return approvals.selectList(new LambdaQueryWrapper<TrainingPlanApproval>()
    .eq(TrainingPlanApproval::getOrganizationId,TenantContext.requireTenantId()).eq(TrainingPlanApproval::getTrainingPlanId,id).orderByDesc(TrainingPlanApproval::getApprovalId)); }
  private TrainingPlanApproval latest(Long id) { var rows=records(id);return rows.isEmpty()?null:rows.get(0); }
  private BusinessException error(int code,String msg) { return new BusinessException(HttpStatus.CONFLICT,code,msg); }
}

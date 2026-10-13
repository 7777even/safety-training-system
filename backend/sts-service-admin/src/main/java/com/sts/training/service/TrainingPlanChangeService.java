package com.sts.training.service;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sts.admin.tenant.TenantContext;
import com.sts.common.exception.BusinessException;
import com.sts.training.dto.*;
import com.sts.training.entity.*;
import com.sts.training.mapper.*;
import com.sts.training.vo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
/** 变更审批和原子生效在同一计划锁、同一事务中完成。 */
@Service @RequiredArgsConstructor public class TrainingPlanChangeService {
  /** 生效计划。 */ private final TrainingPlanMapper plans;
  /** 独立变更。 */ private final TrainingPlanChangeMapper changes;
  /** 统一行锁和审批资格。 */ private final TrainingPlanApprovalService approval;
  /** 复用编排校验及冻结能力。 */ private final TrainingPlanServiceImpl planService;
  /** 完整变更事实保护。 */ private final TrainingPlanChangeSafety safety;
  /** 版本化请求快照。 */ private final ObjectMapper json;
  /** 完整变更年度关联。 */ private final TrainingAnnualPlanService annual;
  /** 当前机构计划的变更历史与草稿。 */
  public List<TrainingPlanChangeVO> list(Long id) {var p=plans.selectById(id);requireVisible(p);return rows(id).stream().map(this::vo).toList();}
  /** 保存独立草稿，不修改生效计划；驳回后可再次编辑。 */
  @Transactional public Long save(Long id,Long changeId,TrainingPlanChangeRequest req,Long actor) {
    var p=approval.lock(id);check(p,req);var row=changeId==null?new TrainingPlanChange():requireChange(id,changeId);
    if(changeId!=null) {
      if(!Objects.equals(row.getSubmitterUserId(),actor)) throw error("仅提交人可编辑变更草稿");
      if(!List.of("DRAFT","REJECTED").contains(row.getChangeStatus()))throw error("仅草稿或驳回变更可编辑");
      if(!Objects.equals(row.getVersion(),req.getVersion()))throw error("变更记录版本冲突");
    }
    row.setOrganizationId(TenantContext.requireTenantId());row.setTrainingPlanId(id);row.setBaseVersion(p.getVersion());row.setSubmitterUserId(actor);
    row.setChangeStatus("DRAFT");row.setRequestJson(write(req));row.setBeforeSnapshotJson(write(planService.get(id)));row.setBeforeParticipantsJson(write(planService.listParticipants(id)));
    if(changeId==null) changes.insert(row);else if(changes.updateById(row)==0)throw error("变更记录版本冲突");return row.getChangeId();
  }
  /** 指定其他审批人；完整变更人员在此展开成固定名单。 */
  @Transactional public void submit(Long id,Long changeId,Integer version,Long approver,Long actor) {
    var p=approval.lock(id);var r=requireChange(id,changeId);
    if(!Objects.equals(r.getSubmitterUserId(),actor))throw error("仅提交人可提交变更");
    if(!Objects.equals(r.getVersion(),version) || !"DRAFT".equals(r.getChangeStatus()))throw error("变更版本或状态冲突");
    var req=read(r.getRequestJson(),TrainingPlanChangeRequest.class);check(p,req);approval.requireApprover(actor,approver);
    if("FULL".equals(req.getChangeType())) {
      planService.freezeChangeParticipants(req.getFull());
      req.getFull().setAnnualSnapshot(req.getFull().getAnnual()==null ? null : annual.freezeSnapshot(req.getFull().getAnnual().getAnnualPlanId()));
    }
    r.setRequestJson(write(req));r.setApproverUserId(approver);r.setChangeStatus("PENDING");
    if(changes.updateById(r)==0)throw error("变更记录版本冲突");
  }
  /** 指定人审批；批准前重新检查版本、执行状态及事实，失败整笔回滚。 */
  @Transactional public void decide(Long id,Long changeId,boolean approved,String comment,Long actor) {
    var p=approval.lock(id);var r=requireChange(id,changeId);
    if(!"PENDING".equals(r.getChangeStatus()))throw error("没有待审批变更");
    if(!Objects.equals(actor,r.getApproverUserId()))throw error("仅指定审批人可审批变更");
    if(approved && !Objects.equals(r.getBaseVersion(),p.getVersion()))throw error("原计划版本已变化，请重新创建变更申请");
    approval.requireApprover(r.getSubmitterUserId(),actor);
    if(!approved && (comment==null || comment.isBlank()))throw error("驳回时请填写审批意见");
    if(approved) {var req=read(r.getRequestJson(),TrainingPlanChangeRequest.class);check(p,req);planService.applyApprovedChange(p,req);
      if("FULL".equals(req.getChangeType()) && req.getFull().getAnnual()!=null) {
        var bind=req.getFull().getAnnual();bind.setVersion(plans.selectById(id).getVersion());annual.bindApprovedChange(id,bind,req.getFull().getAnnualSnapshot());
      }}
    r.setChangeStatus(approved?"APPROVED":"REJECTED");r.setDecisionComment(comment);
    if(changes.updateById(r)==0)throw error("变更记录版本冲突");
  }
  /** BASIC严格只有四字段，FULL不接受执行中或已有安排的请求。 */
  private void check(TrainingPlan p,TrainingPlanChangeRequest req) {
    requireVisible(p);
    if(!List.of("PUBLISHED","RUNNING").contains(p.getPublishStatus()))throw error("草稿、结束、取消或归档计划禁止变更");
    if(!Objects.equals(p.getVersion(),req.getBaseVersion()))throw error("原计划版本冲突");
    if("FULL".equals(req.getChangeType())) {
      if(!"PUBLISHED".equals(p.getPublishStatus()))throw error("进行中仅允许名称、负责人、地点、说明四项变更");
      if(req.getFull()==null || req.getBasic()!=null)throw error("完整变更内容无效");
      safety.requireFullChangeSafe(p);approval.requireManager(req.getFull().getPlan().getManagerUserId());
      // 新编排不能从客户端携带已完成状态或历史成绩。
      for(var stage:req.getFull().getStages()) {stage.setStageStatus("PENDING");if(stage.getContents()!=null)stage.getContents().forEach(c->c.setContentStatus("PENDING"));}
    } else if("BASIC".equals(req.getChangeType())) {
      if(req.getBasic()==null || req.getFull()!=null)throw error("基础变更仅接受四项内容");approval.requireManager(req.getBasic().getManagerUserId());
    } else throw error("变更类型无效");
  }
  private void requireVisible(TrainingPlan p) {if(p==null || !Objects.equals(p.getOrganizationId(),TenantContext.requireTenantId()))throw error("培训计划不存在");}
  private TrainingPlanChange requireChange(Long id,Long changeId) {var r=changes.selectById(changeId);if(r==null || !Objects.equals(r.getOrganizationId(),TenantContext.requireTenantId()) || !Objects.equals(id,r.getTrainingPlanId()))throw error("变更申请不存在");return r;}
  private List<TrainingPlanChange> rows(Long id) {return changes.selectList(new LambdaQueryWrapper<TrainingPlanChange>().eq(TrainingPlanChange::getOrganizationId,TenantContext.requireTenantId()).eq(TrainingPlanChange::getTrainingPlanId,id).orderByDesc(TrainingPlanChange::getChangeId));}
  private TrainingPlanChangeVO vo(TrainingPlanChange r) {var v=new TrainingPlanChangeVO();BeanUtils.copyProperties(r,v);v.setRequest(read(r.getRequestJson(),TrainingPlanChangeRequest.class));v.setBefore(read(r.getBeforeSnapshotJson(),TrainingPlanVO.class));
    try {v.setBeforeParticipants(json.readValue(r.getBeforeParticipantsJson(),json.getTypeFactory().constructCollectionType(List.class,TrainingParticipantVO.class)));}
    catch(JsonProcessingException e){throw error("原参训名单快照无法读取");}return v;}
  private String write(Object value) {try {return json.writeValueAsString(value);}catch(JsonProcessingException e){throw error("变更内容无法保存");}}
  private <T> T read(String value,Class<T> type) {try {return json.readValue(value,type);}catch(JsonProcessingException e){throw error("变更内容无法读取");}}
  private BusinessException error(String msg) {return new BusinessException(HttpStatus.CONFLICT,8350,msg);}
}

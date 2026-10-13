package com.sts.training.service;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sts.admin.mapper.SysFileInfoMapper;
import com.sts.admin.storage.StorageService;
import com.sts.admin.tenant.TenantContext;
import com.sts.common.exception.BusinessException;
import com.sts.training.dto.*;
import com.sts.training.entity.*;
import com.sts.training.mapper.*;
import com.sts.training.vo.TrainingAnnualPlanVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
/** 年度文件维护及审批前关联，不解析文件或自动生成计划。 */
@Service @RequiredArgsConstructor public class TrainingAnnualPlanService {
  /** 年度文件。 */ private final TrainingAnnualPlanMapper annuals;
  /** 冻结关联。 */ private final TrainingAnnualPlanLinkMapper links;
  /** 计划版本。 */ private final TrainingPlanMapper plans;
  /** 统一文件授权。 */ private final SysFileInfoMapper files;
  /** 原始文件存储。 */ private final StorageService storage;
  /** 草稿审批保护。 */ private final TrainingPlanApprovalService approval;
  /** 本机构年度文件。 */
  public List<TrainingAnnualPlanVO> list() { return annuals.selectList(new LambdaQueryWrapper<TrainingAnnualPlan>()
    .eq(TrainingAnnualPlan::getOrganizationId,TenantContext.requireTenantId()).orderByDesc(TrainingAnnualPlan::getPlanYear,TrainingAnnualPlan::getAnnualPlanId))
    .stream().map(this::vo).toList(); }
  /** 上传后保存，版本冲突不覆盖别人的维护。 */
  @Transactional public Long save(Long id,TrainingAnnualPlanRequest req) {
    var file=requireFile(req.getFileInfoId());var r=id==null?new TrainingAnnualPlan():requireAnnual(id);
    if(id!=null && !Objects.equals(req.getVersion(),r.getVersion())) throw error("年度计划版本冲突");
    BeanUtils.copyProperties(req,r);r.setOrganizationId(TenantContext.requireTenantId());r.setPlanName(req.getPlanName().trim());r.setFileName(file.getFileName());
    if(id==null) annuals.insert(r);else if(annuals.updateById(r)==0) throw error("年度计划版本冲突");return r.getAnnualPlanId();
  }
  /** 按计划读取冻结关联，不把当前年度记录当作已批准版本。 */
  public TrainingAnnualPlanVO linked(Long id) { requireVisiblePlan(id);var r=link(id);if(r==null || r.getAnnualPlanId()==null)return null;
    var v=new TrainingAnnualPlanVO();BeanUtils.copyProperties(r,v);return v; }
  /** 草稿关联属于审批内容，因此推进计划版本。 */
  @Transactional public void bind(Long id,TrainingAnnualPlanBindRequest req) { bindInternal(id,req,false,null); }
  /** 仅供完整变更批准事务关联年度依据，无单独HTTP入口。 */
  @Transactional public void bindApprovedChange(Long id,TrainingAnnualPlanBindRequest req,TrainingAnnualPlanVO snapshot) { bindInternal(id,req,true,snapshot); }
  private void bindInternal(Long id,TrainingAnnualPlanBindRequest req,boolean approvedChange,TrainingAnnualPlanVO snapshot) {
    var p=approval.lockForEdit(id);
    if(!(approvedChange ? "PUBLISHED" : "DRAFT").equals(p.getPublishStatus())) throw error("年度关联计划状态不允许修改");
    if(!Objects.equals(p.getVersion(),req.getVersion())) throw error("培训计划版本冲突");
    var r=link(id);boolean fresh=r==null;if(fresh)r=new TrainingAnnualPlanLink();
    if(req.getAnnualPlanId()!=null) {
      if(approvedChange) {
        if(snapshot==null || !Objects.equals(snapshot.getAnnualPlanId(),req.getAnnualPlanId()))throw error("年度审批快照缺失");
        requireFile(snapshot.getFileInfoId());BeanUtils.copyProperties(snapshot,r,"version","createTime");
      } else {var a=requireAnnual(req.getAnnualPlanId());requireFile(a.getFileInfoId());BeanUtils.copyProperties(a,r,"version","createBy","createTime","updateBy","updateTime","deleted");}
    }
    else {r.setAnnualPlanId(null);r.setPlanYear(null);r.setPlanName(null);r.setFileInfoId(null);r.setFileName(null);}
    r.setTrainingPlanId(id);r.setOrganizationId(TenantContext.requireTenantId());
    if(fresh)links.insert(r);else if(links.updateById(r)==0)throw error("年度关联版本冲突");
    if(plans.updateById(p)==0)throw error("培训计划版本冲突");
  }
  /** 提交变更时冻结年度依据，不随公司文件后续维护漂移。 */
  public TrainingAnnualPlanVO freezeSnapshot(Long id) {if(id==null)return null;var a=requireAnnual(id);requireFile(a.getFileInfoId());return vo(a);}
  /** 下载当前年度文件。 */
  public TrainingAttachmentFile file(Long id) { return open(requireAnnual(id).getFileInfoId()); }
  /** 下载计划冻结文件，修改年度文件后仍可审阅原依据。 */
  public TrainingAttachmentFile linkedFile(Long id) {requireVisiblePlan(id);var r=link(id);if(r==null || r.getFileInfoId()==null)throw error("计划没有年度附件");return open(r.getFileInfoId());}
  private TrainingAnnualPlanLink link(Long id) {return links.selectOne(new LambdaQueryWrapper<TrainingAnnualPlanLink>().eq(TrainingAnnualPlanLink::getOrganizationId,TenantContext.requireTenantId()).eq(TrainingAnnualPlanLink::getTrainingPlanId,id));}
  private TrainingAnnualPlan requireAnnual(Long id) {var r=annuals.selectById(id);if(r==null || !Objects.equals(r.getOrganizationId(),TenantContext.requireTenantId()))throw error("年度计划不存在");return r;}
  private void requireVisiblePlan(Long id) {var p=plans.selectById(id);if(p==null || !Objects.equals(p.getOrganizationId(),TenantContext.requireTenantId()))throw error("培训计划不存在");}
  private com.sts.admin.entity.SysFileInfo requireFile(Long id) {var f=files.selectById(id);if(f==null || f.getObjectName()==null || f.getObjectName().isBlank())throw error("年度文件不存在或不可见");return f;}
  private TrainingAttachmentFile open(Long id) {var f=requireFile(id);var resource=storage.download(f.getObjectName());if(resource==null || !resource.exists())throw error("年度文件无法读取");return new TrainingAttachmentFile(resource,f.getFileName(),f.getFileSize()==null?0:f.getFileSize());}
  private TrainingAnnualPlanVO vo(TrainingAnnualPlan r) {var v=new TrainingAnnualPlanVO();BeanUtils.copyProperties(r,v);return v;}
  private BusinessException error(String msg) {return new BusinessException(HttpStatus.CONFLICT,8340,msg);}
}

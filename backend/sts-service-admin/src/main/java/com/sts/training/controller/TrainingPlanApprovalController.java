package com.sts.training.controller;
import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import cn.dev33.satoken.stp.StpUtil;
import com.sts.training.dto.*;
import com.sts.training.service.*;
import com.sts.training.vo.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
/** 年度文件和首次审批；执行状态继续由现有计划接口负责。 */
@RestController @RequiredArgsConstructor public class TrainingPlanApprovalController {
  /** 单级审批。 */ private final TrainingPlanApprovalService approval;
  /** 年度依据。 */ private final TrainingAnnualPlanService annual;
  /** 审批历史。 */ @GetMapping("/training/plans/{id}/approvals") @SaCheckPermission("training:plan:list")
  public List<TrainingPlanApprovalVO> history(@PathVariable Long id) {return approval.history(id);}
  /** 可选审批人。 */ @GetMapping("/training/approval-approvers") @SaCheckPermission(value={"training:plan:edit","training:plan:change"},mode=SaMode.OR)
  public List<TrainingApproverVO> approvers() {return approval.approvers(StpUtil.getLoginIdAsLong());}
  /** 指定其他审批人提交。 */ @PostMapping("/training/plans/{id}/approvals") @SaCheckPermission("training:plan:edit")
  public void submit(@PathVariable Long id,@Valid @RequestBody TrainingPlanApprovalRequest req) {approval.submit(id,req.getVersion(),req.getApproverUserId(),StpUtil.getLoginIdAsLong());}
  /** 审批结论。 */ @PostMapping("/training/plans/{id}/approval-decision") @SaCheckPermission("training:plan:approve")
  public void decide(@PathVariable Long id,@Valid @RequestBody TrainingPlanDecisionRequest req) {approval.decide(id,req.getApproved(),req.getComment(),StpUtil.getLoginIdAsLong());}
  /** 年度文件列表。 */ @GetMapping("/training/annual-plans") @SaCheckPermission("training:annual:list")
  public List<TrainingAnnualPlanVO> list() {return annual.list();}
  /** 新年度文件。 */ @PostMapping("/training/annual-plans") @SaCheckPermission("training:annual:edit")
  public Long create(@Valid @RequestBody TrainingAnnualPlanRequest req) {return annual.save(null,req);}
  /** 更新年度文件。 */ @PutMapping("/training/annual-plans/{id}") @SaCheckPermission("training:annual:edit")
  public void update(@PathVariable Long id,@Valid @RequestBody TrainingAnnualPlanRequest req) {annual.save(id,req);}
  /** 冻结关联。 */ @GetMapping("/training/plans/{id}/annual-plan") @SaCheckPermission("training:plan:list")
  public TrainingAnnualPlanVO linked(@PathVariable Long id) {return annual.linked(id);}
  /** 审批前关联年度依据。 */ @PutMapping("/training/plans/{id}/annual-plan") @SaCheckPermission({"training:plan:edit","training:annual:list"})
  public void bind(@PathVariable Long id,@Valid @RequestBody TrainingAnnualPlanBindRequest req) {annual.bind(id,req);}
  /** 年度文件受控下载。 */ @GetMapping("/training/annual-plans/{id}/file") @SaCheckPermission("training:annual:list")
  public ResponseEntity<Resource> file(@PathVariable Long id) {return download(annual.file(id));}
  /** 计划冻结文件受控下载。 */ @GetMapping("/training/plans/{id}/annual-plan/file") @SaCheckPermission("training:plan:list")
  public ResponseEntity<Resource> linkedFile(@PathVariable Long id) {return download(annual.linkedFile(id));}
  private ResponseEntity<Resource> download(TrainingAttachmentFile f) {return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM)
    .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(f.fileName(),StandardCharsets.UTF_8).build().toString()).body(f.resource());}
}

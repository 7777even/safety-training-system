package com.sts.training.controller;
import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import com.sts.training.dto.*;
import com.sts.training.service.TrainingPlanChangeService;
import com.sts.training.vo.TrainingPlanChangeVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;
/** 已发布计划的独立变更草稿、审批与事务生效。 */
@RestController @RequiredArgsConstructor public class TrainingPlanChangeController {
  /** 变更审批业务。 */ private final TrainingPlanChangeService service;
  /** 当前计划全部变更记录。 */ @GetMapping("/training/plans/{id}/changes") @SaCheckPermission("training:plan:list")
  public List<TrainingPlanChangeVO> list(@PathVariable Long id) {return service.list(id);}
  /** 创建独立草稿。 */ @PostMapping("/training/plans/{id}/changes") @SaCheckPermission("training:plan:change")
  public Long create(@PathVariable Long id,@Valid @RequestBody TrainingPlanChangeRequest req) {return service.save(id,null,req,StpUtil.getLoginIdAsLong());}
  /** 编辑自己未提交的变更。 */ @PutMapping("/training/plans/{id}/changes/{changeId}") @SaCheckPermission("training:plan:change")
  public void update(@PathVariable Long id,@PathVariable Long changeId,@Valid @RequestBody TrainingPlanChangeRequest req) {service.save(id,changeId,req,StpUtil.getLoginIdAsLong());}
  /** 冻结内容并指定另一审批人。 */ @PostMapping("/training/plans/{id}/changes/{changeId}/submit") @SaCheckPermission("training:plan:change")
  public void submit(@PathVariable Long id,@PathVariable Long changeId,@Valid @RequestBody TrainingPlanApprovalRequest req) {service.submit(id,changeId,req.getVersion(),req.getApproverUserId(),StpUtil.getLoginIdAsLong());}
  /** 批准后生效，失败保留原计划及待审变更。 */ @PostMapping("/training/plans/{id}/changes/{changeId}/decision") @SaCheckPermission("training:plan:approve")
  public void decide(@PathVariable Long id,@PathVariable Long changeId,@Valid @RequestBody TrainingPlanDecisionRequest req) {service.decide(id,changeId,req.getApproved(),req.getComment(),StpUtil.getLoginIdAsLong());}
}

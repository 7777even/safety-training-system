package com.sts.evaluation.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.sts.evaluation.dto.ExpertInspectionSubmitRequest;
import com.sts.evaluation.service.EvaluationRecordService;
import com.sts.evaluation.service.ExpertInspectionService;
import com.sts.evaluation.vo.EvaluationRecordVO;
import com.sts.evaluation.vo.ExpertInspectionSummaryVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 专家查课提交与评价记录查询。综合计算批次不再对外提供。
 */
@RestController
@RequiredArgsConstructor
public class EvaluationResultController {

  private final EvaluationRecordService evaluationRecordService;
  private final ExpertInspectionService expertInspectionService;

  /**
   * 查询评价记录。
   */
  @GetMapping("/evaluation/records/{evaluationRecordId}")
  @SaCheckPermission("evaluation:record:list")
  public EvaluationRecordVO getRecord(@PathVariable Long evaluationRecordId) {
    return evaluationRecordService.get(evaluationRecordId);
  }

  /** 在运行中计划内原子提交一份专家查课记录，评价人由登录态确定。 */
  @PostMapping("/evaluation/expert-inspections/submit")
  @SaCheckPermission({"evaluation:record:add", "evaluation:record:submit"})
  public EvaluationRecordVO submitExpertInspection(@RequestBody @Valid ExpertInspectionSubmitRequest request) {
    return expertInspectionService.submit(request);
  }

  /** 查询计划内课程的专家查课提交状态和只读摘要。 */
  @GetMapping("/training/plans/{trainingPlanId}/expert-inspections")
  @SaCheckPermission("training:plan:list")
  public List<ExpertInspectionSummaryVO> listExpertInspections(@PathVariable Long trainingPlanId) {
    return expertInspectionService.listByPlan(trainingPlanId);
  }
}

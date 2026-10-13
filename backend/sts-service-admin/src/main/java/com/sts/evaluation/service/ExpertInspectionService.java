package com.sts.evaluation.service;

import com.sts.evaluation.dto.ExpertInspectionSubmitRequest;
import com.sts.evaluation.vo.EvaluationRecordVO;
import com.sts.evaluation.vo.ExpertInspectionSummaryVO;

import java.util.List;

/** 专家查课的原子提交与计划内只读状态查询。 */
public interface ExpertInspectionService {
  EvaluationRecordVO submit(ExpertInspectionSubmitRequest request);
  List<ExpertInspectionSummaryVO> listByPlan(Long trainingPlanId);
}

package com.sts.evaluation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/** 专家针对计划内单门课程一次性提交的查课记录。 */
@Data
public class ExpertInspectionSubmitRequest {
  @NotNull private Long trainingPlanId;
  @NotNull private Long planContentId;
  @NotNull private Long formVersionId;
  private String summary;
  @Valid @NotEmpty private List<EvaluationRecordItemRequest> items;
}

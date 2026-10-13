package com.sts.training.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TrainingPlanEvaluationFormItemRequest {
  @NotNull private Long formVersionId;
  @NotBlank private String usageType;
  @NotNull private Integer isRequired;
  @NotNull private Integer status;
}

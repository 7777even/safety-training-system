package com.sts.training.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TrainingPlanApplyTemplateRequest {
  @NotNull
  private Integer version;
  @NotNull
  private Long sourceTemplateId;
}

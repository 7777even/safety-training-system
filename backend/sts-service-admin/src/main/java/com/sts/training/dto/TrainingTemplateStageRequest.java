package com.sts.training.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class TrainingTemplateStageRequest {
  @NotBlank private String stageName;
  @NotBlank private String stageType;
  private BigDecimal requiredHours;
  @NotNull private Integer sortOrder;
  @NotNull private Integer status;
  @Valid private List<TrainingTemplateContentRequest> contents;
}

package com.sts.training.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class TrainingTemplateContentRequest {
  @NotBlank private String contentType;
  private Long resourceId;
  private Long resourceVersionId;
  private String assessmentMode;
  @Valid
  private TrainingContentExecutionConfig executionConfig;
  @NotBlank private String contentName;
  @NotNull private Integer isRequired;
  private BigDecimal requiredHours;
  @NotNull private Integer sortOrder;
  @NotNull private Integer status;
}

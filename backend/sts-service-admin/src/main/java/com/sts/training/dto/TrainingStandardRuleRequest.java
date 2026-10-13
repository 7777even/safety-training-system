package com.sts.training.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class TrainingStandardRuleRequest {
  @NotBlank private String ruleType;
  private BigDecimal requiredValue;
  private String unit;
  private String resourceType;
  private Long resourceId;
  @NotNull private Integer sortOrder;
  @NotNull private Integer status;
  @NotBlank private String description;
}

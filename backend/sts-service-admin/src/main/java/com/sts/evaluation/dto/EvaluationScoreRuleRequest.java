package com.sts.evaluation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class EvaluationScoreRuleRequest {
  @NotBlank
  private String evaluationItemCode;
  @NotBlank private String calculationType;
  @NotBlank private String sourceType;
  private BigDecimal scoreChange;
  private BigDecimal weight;
  @NotNull private Integer sortOrder;
  @NotBlank private String ruleDescription;
}

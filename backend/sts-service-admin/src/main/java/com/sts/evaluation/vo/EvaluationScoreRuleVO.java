package com.sts.evaluation.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class EvaluationScoreRuleVO {
  private Long scoreRuleId;
  private Long formVersionId;
  private Long evaluationItemId;
  private String calculationType;
  private String sourceType;
  private BigDecimal scoreChange;
  private BigDecimal weight;
  private Integer sortOrder;
  private String ruleDescription;
}

package com.sts.training.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class TrainingStandardRuleVO {
  private Long standardRuleId;
  private Long trainingStandardId;
  private String ruleType;
  private BigDecimal requiredValue;
  private String unit;
  private String resourceType;
  private Long resourceId;
  private Integer sortOrder;
  private Integer status;
  private String description;
}

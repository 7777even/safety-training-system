package com.sts.evaluation.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class EvaluationItemVO {
  private Long evaluationItemId;
  private Long formVersionId;
  private String itemCode;
  private String itemName;
  private String itemType;
  private BigDecimal maxScore;
  private BigDecimal weight;
  private Integer sortOrder;
  private Integer isRequired;
}

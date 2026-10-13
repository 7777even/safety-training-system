package com.sts.evaluation.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class EvaluationRecordItemVO {
  private Long recordItemId;
  private Long evaluationItemId;
  private BigDecimal score;
  private String textValue;
  private String optionValue;
}

package com.sts.evaluation.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class EvaluationRecordItemRequest {
  @NotNull
  private Long evaluationItemId;
  private BigDecimal score;
  private String textValue;
  private String optionValue;
}

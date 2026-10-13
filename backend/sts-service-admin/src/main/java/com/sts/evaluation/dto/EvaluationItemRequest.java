package com.sts.evaluation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class EvaluationItemRequest {
  @NotBlank private String itemCode;
  @NotBlank private String itemName;
  @NotBlank private String itemType;
  private BigDecimal maxScore;
  private BigDecimal weight;
  @NotNull private Integer sortOrder;
  @NotNull private Integer isRequired;
}

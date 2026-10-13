package com.sts.question.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class QuestionKeywordRequest {
  @NotBlank private String keyword;
  private BigDecimal weight;
  @NotNull private Integer sortOrder;
}

package com.sts.exam.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class ReviewAnswerRequest {
  @NotNull private BigDecimal afterScore;
  private String reviewComment;
  private String reviewAction = "SCORE";
}

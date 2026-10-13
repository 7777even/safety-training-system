package com.sts.paper.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class PaperDrawRuleRequest {
  @NotNull private Long questionBankId;
  @NotBlank private String questionType;
  private String difficulty = "ALL";
  private Long knowledgePointId = 0L;
  @NotNull private Integer drawCount;
  @NotNull private BigDecimal scorePerQuestion;
  private BigDecimal bankWeight;
  @NotNull private Integer sortOrder;
}

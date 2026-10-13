package com.sts.paper.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class PaperQuestionItemRequest {
  @NotNull private Long questionId;
  private Long questionVersionId;
  @NotNull private BigDecimal score;
  @NotNull private Integer sortOrder;
}

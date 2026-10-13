package com.sts.question.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class QuestionOptionRequest {
  @NotBlank private String optionKey;
  @NotBlank private String optionContent;
  @NotNull private Integer isCorrect;
  @NotNull private Integer sortOrder;
}

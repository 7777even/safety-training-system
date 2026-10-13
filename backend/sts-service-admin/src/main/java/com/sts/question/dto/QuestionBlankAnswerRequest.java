package com.sts.question.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class QuestionBlankAnswerRequest {
  @NotNull private Integer blankNo;
  @NotNull private Integer answerGroupNo;
  @NotBlank private String answerText;
  @NotNull private Integer sortOrder;
}

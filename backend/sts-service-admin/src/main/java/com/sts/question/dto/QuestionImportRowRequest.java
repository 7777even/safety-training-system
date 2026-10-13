package com.sts.question.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.util.List;

@Data
public class QuestionImportRowRequest {
  @NotBlank private String questionCode;
  @NotBlank private String questionType;
  @NotBlank private String stem;
  private String analysis;
  private String standardAnswer;
  @NotBlank private String difficulty;
  private String gradingMode;
  private List<QuestionOptionRequest> options;
  private List<QuestionBlankAnswerRequest> blankAnswers;
}

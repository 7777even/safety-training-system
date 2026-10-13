package com.sts.survey.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 单题作答：选择题提交 optionIds，文本题提交 answerText。
 */
@Data
public class SurveyAnswerRequest {
  @NotNull
  private Long surveyQuestionId;
  private String answerText;
  private List<Long> optionIds;
}

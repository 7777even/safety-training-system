package com.sts.survey.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class SurveyQuestionsReplaceRequest {
  @NotNull
  private Integer version;
  @Valid
  @NotNull
  private List<SurveyQuestionRequest> questions;
}

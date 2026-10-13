package com.sts.survey.dto;

import lombok.Data;

@Data
public class SurveyInstanceQuery {
  private Long surveyId;
  private String instanceStatus;
}

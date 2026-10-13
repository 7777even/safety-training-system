package com.sts.survey.dto;

import lombok.Data;

@Data
public class SurveyQuery {
  private String surveyCode;
  private String surveyName;
  private String contentStatus;
  private Integer status;
}

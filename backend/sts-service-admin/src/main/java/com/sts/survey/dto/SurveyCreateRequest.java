package com.sts.survey.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 创建问卷；用途仅限线下培训调查或培训机构评价。
 */
@Data
public class SurveyCreateRequest {
  @NotBlank
  private String surveyCode;
  @NotBlank
  private String surveyName;
  /**
   * 问卷用途：TRAINING 线下培训调查，ORGANIZATION_EVALUATION 培训机构评价。
   */
  @NotBlank
  private String surveyType;
  private String description;
  @NotNull
  private Integer status;
}

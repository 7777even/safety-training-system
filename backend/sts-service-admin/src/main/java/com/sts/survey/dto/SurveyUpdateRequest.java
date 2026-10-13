package com.sts.survey.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 更新未投放问卷的基本信息。
 */
@Data
public class SurveyUpdateRequest {
  @NotNull
  private Integer version;
  @NotBlank
  private String surveyName;
  private String description;
  @NotNull
  private Integer status;
}

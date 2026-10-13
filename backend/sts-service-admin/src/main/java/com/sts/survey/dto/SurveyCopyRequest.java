package com.sts.survey.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 复制问卷请求；生成一份未投放草稿。
 */
@Data
public class SurveyCopyRequest {
  /**
   * 新问卷编码，同机构唯一。
   */
  @NotBlank
  private String surveyCode;
  /**
   * 新问卷名称。
   */
  @NotBlank
  private String surveyName;
}

package com.sts.survey.vo;

import lombok.Data;

/**
 * 单个选项的作答计数。
 */
@Data
public class SurveyOptionCountVO {
  private Long surveyOptionId;
  private String optionContent;
  private Long count;
}

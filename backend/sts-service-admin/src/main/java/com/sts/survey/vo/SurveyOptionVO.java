package com.sts.survey.vo;

import lombok.Data;

/**
 * 单选/多选题选项。
 */
@Data
public class SurveyOptionVO {
  private Long surveyOptionId;
  private String optionKey;
  private String optionContent;
  private Integer sortOrder;
}

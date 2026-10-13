package com.sts.survey.vo;

import lombok.Data;

import java.util.List;

/**
 * 问卷题目（设计、预览和填写共用）。
 */
@Data
public class SurveyQuestionVO {
  private Long surveyQuestionId;
  /**
   * 题型：SINGLE 单选，MULTIPLE 多选，TEXT 文本。
   */
  private String questionType;
  private String questionText;
  private Integer isRequired;
  private Integer sortOrder;
  private List<SurveyOptionVO> options;
}

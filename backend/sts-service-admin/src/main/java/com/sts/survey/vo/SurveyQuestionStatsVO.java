package com.sts.survey.vo;

import lombok.Data;

import java.util.List;

/**
 * 单题统计；选择题给选项计数，文本题给答案列表。
 */
@Data
public class SurveyQuestionStatsVO {
  private Long surveyQuestionId;
  private String questionType;
  private String questionText;
  private List<SurveyOptionCountVO> optionCounts;
  private List<String> textAnswers;
}

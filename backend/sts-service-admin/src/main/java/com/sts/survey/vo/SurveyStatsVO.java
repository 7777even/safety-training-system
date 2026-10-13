package com.sts.survey.vo;

import lombok.Data;

import java.util.List;

/**
 * 投放结果统计：答卷数、选项计数、文本答案列表。
 */
@Data
public class SurveyStatsVO {
  private Long surveyInstanceId;
  private Long totalSubmitted;
  private List<SurveyQuestionStatsVO> questionStats;
}

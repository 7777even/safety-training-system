package com.sts.survey.vo;

import lombok.Data;

import java.util.List;

/**
 * 电脑或手机预览结果；题目内容相同，client 供前端选择展示形态。
 */
@Data
public class SurveyPreviewVO {
  /**
   * 预览端：PC 电脑，MOBILE 手机。
   */
  private String client;
  private Long surveyId;
  private String surveyName;
  private String surveyType;
  private List<SurveyQuestionVO> questions;
}

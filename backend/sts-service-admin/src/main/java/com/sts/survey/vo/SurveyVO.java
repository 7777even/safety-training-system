package com.sts.survey.vo;

import lombok.Data;

import java.util.List;

/**
 * 问卷详情；get 含题目，分页列表不含题目。
 */
@Data
public class SurveyVO {
  private Long surveyId;
  private Long organizationId;
  private String surveyCode;
  private String surveyName;
  private Long currentVersionId;
  /**
   * TRAINING 线下培训调查，ORGANIZATION_EVALUATION 培训机构评价。
   */
  private String surveyType;
  private String description;
  private String contentStatus;
  private Integer status;
  private Integer version;
  private List<SurveyQuestionVO> questions;
}

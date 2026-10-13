package com.sts.survey.vo;

import lombok.Data;

import java.util.List;

/**
 * 登录用户填写页；题目来自投放时冻结快照。
 */
@Data
public class SurveyFillVO {
  private Long surveyInstanceId;
  private Long surveyId;
  private String instanceName;
  private String surveyName;
  /**
   * 投放用途：TRAINING_SURVEY 线下培训调查，ORG_EVALUATION 培训机构评价。
   */
  private String purpose;
  /**
   * 结果可见方式：ADMIN_ONLY 仅管理员，SUBMITTER_AFTER_SUBMIT 提交后填写人可见。
   */
  private String resultVisibility;
  /**
   * 当前登录用户是否已提交。
   */
  private Boolean submitted;
  private List<SurveyQuestionVO> questions;
}

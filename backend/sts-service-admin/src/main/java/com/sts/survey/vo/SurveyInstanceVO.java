package com.sts.survey.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 问卷投放；含填写链接和二维码内容。
 */
@Data
public class SurveyInstanceVO {
  private Long surveyInstanceId;
  private Long organizationId;
  private String instanceCode;
  private String instanceName;
  private Long surveyId;
  private Long surveyVersionId;
  /**
   * TRAINING_SURVEY 线下培训调查，ORG_EVALUATION 培训机构评价。
   */
  private String purpose;
  /**
   * ADMIN_ONLY 仅管理员，SUBMITTER_AFTER_SUBMIT 提交后填写人可见。
   */
  private String resultVisibility;
  private String instanceStatus;
  private LocalDateTime publishedTime;
  private LocalDateTime startTime;
  private LocalDateTime endTime;
  /**
   * 可访问填写链接。
   */
  private String fillUrl;
  /**
   * 二维码编码内容，等于填写链接。
   */
  private String qrContent;
}

package com.sts.exam.vo;

import lombok.Data;

/**
 * 考试批阅人及其展示信息。
 */
@Data
public class ExamReviewerVO {
  private Long examReviewerId;
  private Long assessmentExecutionId;
  private Long examId;
  private Long reviewerUserId;
  private String reviewerName;
  private String reviewerCode;
  private Long reviewerOrganizationId;
  private String reviewerOrganizationName;
  private String assignmentSource;
}

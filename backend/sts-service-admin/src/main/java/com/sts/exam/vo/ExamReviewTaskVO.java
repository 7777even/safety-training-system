package com.sts.exam.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ExamReviewTaskVO {
  private Long assessmentExecutionId;
  private String sourceType;
  private Long sourceBusinessId;
  private String sourceName;
  private Long examId;
  private String examName;
  private Long courseId;
  private String courseName;
  private Long courseOfferingId;
  private Long courseItemId;
  private String courseItemTitle;
  private Long examCandidateId;
  private Long examAttemptId;
  private Integer attemptNo;
  private String attemptStatus;
  private Long examAnswerId;
  private Long attemptQuestionId;
  private String questionType;
  private String stemSnapshot;
  private BigDecimal score;
  private String answerText;
  /** 题目冻结标准答案，供主观题阅卷参考。 */
  private String standardAnswer;
  private Long learnerUserId;
  private String learnerName;
  private String learnerCode;
  private Long learnerOrganizationId;
  private String learnerOrganizationName;
  private LocalDateTime submittedTime;
  private String reviewStatus;
  private BigDecimal latestManualScore;
  private String latestReviewComment;
  private LocalDateTime latestReviewedTime;
  private String scorePublishStatus;
  private LocalDateTime scorePublishedTime;
}

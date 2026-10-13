package com.sts.exam.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ExamAttemptRecordVO {
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
  private String attemptType;
  private String attemptStatus;
  private Long learnerUserId;
  private String learnerName;
  private String learnerCode;
  private Long learnerOrganizationId;
  private String learnerOrganizationName;
  private LocalDateTime startedTime;
  private LocalDateTime submittedTime;
  private Integer usedSeconds;
  private String reviewStatus;
  private LocalDateTime reviewCompletedTime;
  private BigDecimal objectiveScore;
  private BigDecimal subjectiveScore;
  private BigDecimal finalScore;
  private Integer isPassed;
  private String scorePublishStatus;
  private LocalDateTime scorePublishedTime;
}

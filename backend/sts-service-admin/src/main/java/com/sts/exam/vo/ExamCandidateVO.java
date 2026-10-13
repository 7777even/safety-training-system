package com.sts.exam.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ExamCandidateVO {
  private Long examCandidateId;
  private Long assessmentExecutionId;
  private Long examId;
  private Long userId;
  private String learnerName;
  private String learnerCode;
  private Long learnerOrganizationId;
  private String learnerOrganizationName;
  private Long deptId;
  private String deptName;
  private String sourceType;
  private String candidateStatus;
  private Integer attemptCount;
  private BigDecimal bestScore;
  private BigDecimal finalScore;
  private Long finalAttemptId;
  private LocalDateTime resultPublishedTime;
  private LocalDateTime resultEffectiveTime;
  private Integer version;
}

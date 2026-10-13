package com.sts.exam.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 学员考试任务聚合读模型。
 */
@Data
public class ExamLearnerTaskVO {
  private Long examCandidateId;
  private String candidateStatus;
  private Integer attemptCount;
  private Long examId;
  private String examName;
  private String coverUrl;
  private String examType;
  private String sourceType;
  private Long sourceBusinessId;
  private LocalDateTime openStartTime;
  private LocalDateTime openEndTime;
  private Integer durationSeconds;
  private Long currentAttemptId;
  private String attemptStatus;
  private LocalDateTime deadlineTime;
  private String taskStatus;
  private String scorePublishStatus;
  private LocalDateTime scorePublishedTime;
  private Boolean scoreVisible;
  private BigDecimal finalScore;
  private Integer isPassed;
  private String availableAction;
}

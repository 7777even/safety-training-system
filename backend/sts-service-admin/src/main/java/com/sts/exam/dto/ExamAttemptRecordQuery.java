package com.sts.exam.dto;

import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ExamAttemptRecordQuery {
  private Long examId;
  private String attemptStatus;
  @Pattern(regexp = "NOT_REQUIRED|PENDING|COMPLETED")
  private String reviewStatus;
  @Pattern(regexp = "UNPUBLISHED|PUBLISHED")
  private String scorePublishStatus;
  private String learnerKeyword;
  private LocalDateTime submittedStartTime;
  private LocalDateTime submittedEndTime;
}

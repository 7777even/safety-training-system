package com.sts.exam.dto;

import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ExamReviewTaskQuery {
  private Long examId;
  @Pattern(regexp = "PENDING|REVIEWED")
  private String reviewStatus;
  private String learnerKeyword;
  private LocalDateTime submittedStartTime;
  private LocalDateTime submittedEndTime;
}

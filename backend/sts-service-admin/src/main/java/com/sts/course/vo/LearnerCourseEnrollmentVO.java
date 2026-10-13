package com.sts.course.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class LearnerCourseEnrollmentVO {
  private Long enrollmentId;
  private Long offeringId;
  private Long courseId;
  private String courseName;
  private String offeringName;
  private String courseType;
  private LocalDateTime startTime;
  private LocalDateTime endTime;
  private String learningStatus;
  private BigDecimal progressPercent;
  private LocalDateTime lastStudyTime;
  private LocalDateTime completedTime;
  private String coverUrl;
}

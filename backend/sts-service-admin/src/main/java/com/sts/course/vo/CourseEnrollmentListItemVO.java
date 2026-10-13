package com.sts.course.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class CourseEnrollmentListItemVO {
  private Long courseEnrollmentId;
  private Long courseOfferingId;
  private Long userId;
  private String userCode;
  private String userName;
  private String enrollmentSource;
  private LocalDateTime enrolledTime;
  private String learningStatus;
  private BigDecimal progressPercent;
  private Integer totalStudySeconds;
  private BigDecimal creditAwarded;
  private LocalDateTime completedTime;
  private LocalDateTime lastStudyTime;
}

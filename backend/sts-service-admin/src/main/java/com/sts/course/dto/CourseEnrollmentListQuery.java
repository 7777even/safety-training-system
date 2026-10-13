package com.sts.course.dto;

import lombok.Data;

@Data
public class CourseEnrollmentListQuery {
  private Long userId;
  private String learningStatus;
  private String enrollmentSource;
}

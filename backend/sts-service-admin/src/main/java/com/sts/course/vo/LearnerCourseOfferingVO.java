package com.sts.course.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LearnerCourseOfferingVO {
  private Long offeringId;
  private Long courseId;
  private String courseName;
  private String offeringName;
  private String courseType;
  private LocalDateTime startTime;
  private LocalDateTime endTime;
  private String enrollmentType;
  private String enrollmentAvailability;
  private String coverUrl;
}

package com.sts.course.dto;

import lombok.Data;

@Data
public class LearnerCourseOfferingQuery {
  private String keyword;
  private Long categoryId;
  private String offeringStatus;
}

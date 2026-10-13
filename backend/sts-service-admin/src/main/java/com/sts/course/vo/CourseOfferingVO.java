package com.sts.course.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CourseOfferingVO {
  private Long courseOfferingId;
  private String offeringCode;
  private Long courseId;
  private String courseName;
  private Long courseVersionId;
  private Long planContentId;
  private String offeringName;
  private LocalDateTime startTime;
  private LocalDateTime endTime;
  private String courseType;
  private String enrollmentType;
  private String eligibilityType;
  private String completionRule;
  private String publishStatus;
  private LocalDateTime publishedTime;
  private Integer status;
  private Integer version;
  private LocalDateTime createTime;
  private LocalDateTime updateTime;
}

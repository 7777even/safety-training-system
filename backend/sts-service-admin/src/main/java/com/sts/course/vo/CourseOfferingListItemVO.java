package com.sts.course.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CourseOfferingListItemVO {
  private Long courseOfferingId;
  private String offeringCode;
  private Long courseId;
  private String courseName;
  private Long courseVersionId;
  private String offeringName;
  private LocalDateTime startTime;
  private LocalDateTime endTime;
  private String courseType;
  private String enrollmentType;
  private String eligibilityType;
  private String completionRule;
  /**
   * 来源培训计划内容 ID；独立开课为空。
   */
  private Long planContentId;
  private String publishStatus;
  private LocalDateTime publishedTime;
  private Integer status;
  private LocalDateTime updateTime;
}

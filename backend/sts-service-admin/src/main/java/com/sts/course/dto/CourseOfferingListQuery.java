package com.sts.course.dto;

import lombok.Data;

@Data
public class CourseOfferingListQuery {
  private String offeringName;
  private Long courseId;
  private String publishStatus;
  private String courseType;
  private String enrollmentType;
  private Integer status;
  /**
   * 培训计划内容 ID；传入时只返回该计划内容生成的开课。
   */
  private Long planContentId;
}

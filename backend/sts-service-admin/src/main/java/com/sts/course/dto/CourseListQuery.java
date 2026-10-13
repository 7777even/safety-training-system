package com.sts.course.dto;

import lombok.Data;

@Data
public class CourseListQuery {
  /** 名称或重点内容关键词。 */
  private String courseName;
  /** 主讲教师筛选。 */
  private Long teacherUserId;
  private String publishStatus;
  private Integer status;
  private Long categoryId;
}

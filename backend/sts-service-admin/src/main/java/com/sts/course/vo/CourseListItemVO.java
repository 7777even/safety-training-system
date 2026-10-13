package com.sts.course.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CourseListItemVO {
  private Long courseId;
  private String courseCode;
  private String courseName;
  /** 本机构主讲教师用户ID，旧课程可空。 */
  private Long teacherUserId;
  private Long coverFileId;
  private String contentMode;
  private String publishStatus;
  private Integer status;
  private LocalDateTime updateTime;
}

package com.sts.course.vo;

import lombok.Data;

@Data
public class CourseChapterVO {
  private Long chapterId;
  /** 父章节ID，0为根级。 */
  private Long parentId;
  private Long courseId;
  private String chapterName;
  private Integer sortOrder;
  private Integer status;
  private Integer version;
}

package com.sts.course.vo;

import lombok.Data;

import java.util.List;

@Data
public class LearnerCourseTaskChapterVO {
  private Long chapterId;
  /** 冻结父章节ID，旧快照默认根级。 */
  private Long parentId = 0L;
  private String chapterName;
  private Integer sortOrder;
  private List<LearnerCourseTaskItemVO> items;
}

package com.sts.course.snapshot;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;

import java.util.List;

@Data
@JsonPropertyOrder({"chapterId", "chapterName", "sortOrder", "status", "items"})
public class ChapterSnapshot {
  private Long chapterId;
  /** 冻结父章节ID，旧快照默认根级。 */
  private Long parentId = 0L;
  private String chapterName;
  private Integer sortOrder;
  private Integer status;
  private List<ItemSnapshot> items;
}

package com.sts.course.snapshot;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;

import java.util.List;

@Data
@JsonPropertyOrder({"schemaVersion", "course", "categoryIds", "chapters", "downloadAssets"})
public class CourseContentSnapshot {
  private int schemaVersion = 1;
  private CourseSnapshot course;
  private List<Long> categoryIds;
  private List<ChapterSnapshot> chapters;
  private List<DownloadAssetSnapshot> downloadAssets;
}

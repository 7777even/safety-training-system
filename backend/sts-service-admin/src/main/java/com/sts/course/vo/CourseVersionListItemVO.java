package com.sts.course.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CourseVersionListItemVO {
  private Long courseVersionId;
  private Long courseId;
  private Integer versionNo;
  private String snapshotHash;
  private String versionNote;
  private Long publishedBy;
  private LocalDateTime publishedTime;
}

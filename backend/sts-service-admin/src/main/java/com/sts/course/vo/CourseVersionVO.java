package com.sts.course.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class CourseVersionVO extends CourseVersionListItemVO {
  private String contentSnapshotJson;
}

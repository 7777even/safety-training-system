package com.sts.course.vo;

import lombok.Data;

@Data
public class CourseAssetReferenceVO {
  private Long assetId;
  private Long referenceCount;
  private Long itemCount;
  private Long downloadCount;
}

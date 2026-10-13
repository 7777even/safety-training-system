package com.sts.course.vo;

import lombok.Data;

@Data
public class LearnerCourseDownloadAssetVO {
  private Long assetId;
  private String assetName;
  private String assetType;
  private String fileName;
  private String mimeType;
  private Long fileSize;
  private Long fileInfoId;
  private Integer sortOrder;
  private Boolean downloadable;
  private String downloadUrl;
  private String unavailableReason;
}

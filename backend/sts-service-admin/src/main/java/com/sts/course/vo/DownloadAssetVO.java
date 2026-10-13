package com.sts.course.vo;

import lombok.Data;

@Data
public class DownloadAssetVO {
  private Long courseDownloadAssetId;
  private Long courseId;
  private Long assetId;
  private Integer sortOrder;
  private Integer status;
  private Integer version;
}

package com.sts.course.snapshot;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;

@Data
@JsonPropertyOrder({"assetId", "assetName", "sortOrder", "status"})
public class DownloadAssetSnapshot {
  private Long assetId;
  private String assetName;
  private Integer sortOrder;
  private Integer status;
}

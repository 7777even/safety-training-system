package com.sts.course.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class DownloadAssetReplaceRequest {
  @NotEmpty(message = "下载素材列表不能为空")
  @Valid
  private List<DownloadAssetEntry> assets;

  @Data
  public static class DownloadAssetEntry {
    @NotNull(message = "素材ID不能为空")
    private Long assetId;

    @NotNull(message = "排序不能为空")
    private Integer sortOrder;

    @NotNull(message = "启用状态不能为空")
    private Integer status;
  }
}

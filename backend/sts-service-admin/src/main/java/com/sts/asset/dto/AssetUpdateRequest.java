package com.sts.asset.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AssetUpdateRequest {
  @NotBlank(message = "素材名称不能为空")
  private String assetName;

  @NotNull(message = "状态不能为空")
  private Integer status;

  @NotNull(message = "版本号不能为空")
  private Integer version;
}

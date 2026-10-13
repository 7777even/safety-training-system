package com.sts.asset.dto;

import lombok.Data;

@Data
public class AssetListQuery {
  private String assetName;
  private String assetType;
  private String processStatus;
  private Integer status;
}

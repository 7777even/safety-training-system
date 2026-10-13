package com.sts.asset.vo;

import lombok.Data;

@Data
public class AssetListItemVO {
  private Long assetId;
  private String assetCode;
  private String assetName;
  private String assetType;
  private String processStatus;
  private Integer status;
}

package com.sts.asset.vo;

import lombok.Data;

@Data
public class AssetVO {
  private Long assetId;
  private String assetCode;
  private String assetName;
  private String assetType;
  private String sourceType;
  private Long fileInfoId;
  private String externalUrl;
  private String richContent;
  private String mimeType;
  private Long fileSize;
  private Integer pageCount;
  private Integer durationSeconds;
  private String checksum;
  private String processStatus;
  private Integer status;
  private Integer version;
}

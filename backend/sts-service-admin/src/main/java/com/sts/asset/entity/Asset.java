package com.sts.asset.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sts.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sts_asset_asset")
public class Asset extends BaseEntity {
  @TableId(type = IdType.AUTO)
  private Long assetId;
  private Long organizationId;
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
}

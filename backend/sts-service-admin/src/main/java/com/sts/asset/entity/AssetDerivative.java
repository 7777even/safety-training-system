package com.sts.asset.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sts.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sts_asset_derivative")
public class AssetDerivative extends BaseEntity {
  @TableId(type = IdType.AUTO)
  private Long assetDerivativeId;
  private Long assetId;
  private String derivativeType;
  private Long fileInfoId;
  private Integer durationSeconds;
  private Integer pageCount;
  private Integer status;
}

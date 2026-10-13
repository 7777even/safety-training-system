package com.sts.asset.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sts.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sts_asset_transcode_job")
public class AssetTranscodeJob extends BaseEntity {
  @TableId(type = IdType.AUTO)
  private Long transcodeJobId;
  private Long organizationId;
  private Long assetId;
  private Long courseId;
  private Long chapterId;
  private String jobType;
  private String jobStatus;
  private String providerJobId;
  private LocalDateTime startedTime;
  private LocalDateTime finishedTime;
  private Integer retryCount;
  private String failureCode;
  private String failureReason;
}

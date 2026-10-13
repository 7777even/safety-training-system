package com.sts.asset.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AssetTranscodeJobListItemVO {
  private Long transcodeJobId;
  private Long assetId;
  private String jobType;
  private String jobStatus;
  private LocalDateTime startedTime;
  private LocalDateTime finishedTime;
  private Integer retryCount;
  private String failureCode;
  private String failureReason;
  private LocalDateTime createTime;
}

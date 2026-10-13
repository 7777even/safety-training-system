package com.sts.archive.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ArchiveTrainingVO {
  private Long trainingArchiveId;
  private Long organizationId;
  private String archiveCode;
  private Long trainingPlanId;
  private Integer currentVersionNo;
  private String archiveStatus;
  private LocalDateTime sealedTime;
}

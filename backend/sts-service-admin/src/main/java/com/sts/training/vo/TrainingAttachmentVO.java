package com.sts.training.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TrainingAttachmentVO {
  private Long trainingAttachmentId;
  private Long trainingPlanId;
  private Long planStageId;
  private String attachmentType;
  private Long fileInfoId;
  private String attachmentName;
  private String description;
  private Integer sortOrder;
  private Integer status;
  private Integer version;
  private LocalDateTime createTime;
}

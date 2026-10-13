package com.sts.training.dto;

import lombok.Data;

@Data
public class TrainingAttachmentQuery {
  private Long trainingPlanId;
  private Long planStageId;
  private String attachmentType;
  private Integer status;
}

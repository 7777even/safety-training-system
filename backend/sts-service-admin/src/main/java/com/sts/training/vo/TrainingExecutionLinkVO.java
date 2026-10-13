package com.sts.training.vo;

import lombok.Data;

@Data
public class TrainingExecutionLinkVO {
  private Long executionLinkId;
  private Long trainingPlanId;
  private Long planContentId;
  private String executionType;
  private Long executionId;
}

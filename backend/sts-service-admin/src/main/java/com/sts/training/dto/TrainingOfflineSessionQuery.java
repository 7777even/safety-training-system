package com.sts.training.dto;

import lombok.Data;

@Data
public class TrainingOfflineSessionQuery {
  private Long trainingPlanId;
  private Long planStageId;
  private Long teacherUserId;
  private String sessionStatus;
  private Integer status;
}

package com.sts.training.dto;

import lombok.Data;

@Data
public class TrainingRewardPenaltyQuery {
  private Long trainingPlanId;
  private Long trainingParticipantId;
  private String recordType;
  private Integer status;
}

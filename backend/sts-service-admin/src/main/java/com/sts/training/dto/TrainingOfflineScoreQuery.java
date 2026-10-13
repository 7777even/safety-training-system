package com.sts.training.dto;

import lombok.Data;

@Data
public class TrainingOfflineScoreQuery {
  private Long trainingParticipantId;
  private Long offlineSessionId;
  private String scoreName;
}

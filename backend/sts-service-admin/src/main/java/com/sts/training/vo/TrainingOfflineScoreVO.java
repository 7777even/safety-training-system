package com.sts.training.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class TrainingOfflineScoreVO {
  private Long offlineScoreId;
  private Long organizationId;
  private Long trainingParticipantId;
  private Long offlineSessionId;
  private String scoreName;
  private BigDecimal score;
  private BigDecimal maxScore;
  private String scoreSource;
  private Long importBatchId;
  private Long scoredBy;
  private LocalDateTime scoredTime;
  private Integer version;
}

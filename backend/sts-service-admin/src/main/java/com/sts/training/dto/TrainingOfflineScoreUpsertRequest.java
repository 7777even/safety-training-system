package com.sts.training.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class TrainingOfflineScoreUpsertRequest {
  @NotNull private Long trainingParticipantId;
  @NotNull private Long offlineSessionId;
  @NotBlank private String scoreName;
  @NotNull private BigDecimal score;
  @NotNull private BigDecimal maxScore;
  @NotBlank private String scoreSource;
  private Long importBatchId;
  @NotNull private LocalDateTime scoredTime;
}

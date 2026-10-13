package com.sts.training.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 学员只读的本人线下场次详情。不含出勤和管理端编排字段。
 */
@Data
public class TrainingLearnerOfflineSessionVO {
  private Long offlineSessionId;
  private Long trainingPlanId;
  private Long trainingParticipantId;
  private Long planContentId;
  private String sessionName;
  private String location;
  private LocalDateTime startTime;
  private LocalDateTime endTime;
  private String sessionStatus;
  private BigDecimal score;
  private LocalDateTime scoredTime;
  private String resultStatus;
  private Integer effectiveDurationSeconds;
}

package com.sts.training.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class TrainingRewardPenaltyVO {
  private Long rewardPenaltyId;
  private Long organizationId;
  private Long trainingPlanId;
  private Long trainingParticipantId;
  private String recordType;
  private String title;
  private String description;
  private BigDecimal scoreChange;
  private LocalDate occurredDate;
  private Long evidenceFileId;
  private Integer status;
  private Integer version;
  private LocalDateTime createTime;
}

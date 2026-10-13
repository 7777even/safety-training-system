package com.sts.training.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class TrainingParticipantResultsVO {
  private Long trainingPlanId;
  private Long trainingParticipantId;
  /**
   * 参训人用户 ID，已结束结果页展示学员详情使用。
   */
  private Long userId;
  private BigDecimal courseScore;
  private BigDecimal examScore;
  private BigDecimal offlineScore;
  private BigDecimal compositeScore;
  private Long officialCalculationRunId;
  private String resultStatus;
  private BigDecimal onlineHours;
  private BigDecimal offlineHours;
  private LocalDateTime resultFinalizedTime;
  private List<TrainingParticipantResultItemVO> items;
}

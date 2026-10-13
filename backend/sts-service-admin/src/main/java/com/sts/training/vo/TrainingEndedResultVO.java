package com.sts.training.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 已结束培训正式结果汇总。
 */
@Data
public class TrainingEndedResultVO {
  private Long trainingPlanId;
  private String planCode;
  private String planName;
  private String publishStatus;
  private Long participantCount;
  private Long passedCount;
  private Long failedCount;
  private Long incompleteCount;
  private List<TrainingParticipantResultsVO> participants;
  private List<TrainingRewardPenaltyVO> rewardPenalties;
  private BigDecimal averageCompositeScore;
}

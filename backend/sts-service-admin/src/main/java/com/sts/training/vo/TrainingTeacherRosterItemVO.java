package com.sts.training.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class TrainingTeacherRosterItemVO {
  private Long trainingParticipantId;
  private Long userId;
  /** 参训人在当前用户资料中的展示姓名；历史用户缺失时为空。 */
  private String userName;
  private BigDecimal score;
  private BigDecimal maxScore;
  private LocalDateTime scoredTime;
  private String resultStatus;
}

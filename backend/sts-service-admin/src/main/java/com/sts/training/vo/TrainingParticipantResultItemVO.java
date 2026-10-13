package com.sts.training.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class TrainingParticipantResultItemVO {
  private Long planContentId;
  private String contentName;
  private String executionType;
  private Long executionId;
  private String sourceRecordType;
  private Long sourceRecordId;
  private Long finalAttemptId;
  /**
   * 题库练习可判断题数。
   */
  private Integer evaluatedCount;
  /**
   * 题库练习自动判断正确数。
   */
  private Integer correctCount;
  /**
   * 题库练习自动判断错误数。
   */
  private Integer wrongCount;
  /**
   * 题库练习只记录作答、不判断对错的题目数。
   */
  private Integer unassessedCount;
  /**
   * 仅以可判断题为分母的正确率；无可判断题时为空。
   */
  private BigDecimal accuracyRate;
  private String sourceStatus;
  private String resultStatus;
  /** 线下场次当前状态；仅 executionType 为 OFFLINE 时返回。 */
  private String sessionStatus;
  private Integer effectiveDurationSeconds;
  private BigDecimal score;
  private LocalDateTime completedTime;
  private Integer resultRevision;
  private LocalDateTime finalizedTime;
}

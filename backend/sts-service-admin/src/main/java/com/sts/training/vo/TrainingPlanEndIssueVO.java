package com.sts.training.vo;

import lombok.Data;

/**
 * 计划结束门禁的单条阻塞项。
 */
@Data
public class TrainingPlanEndIssueVO {
  /**
   * 被阻塞的参训人 ID；计划级问题时为空。
   */
  private Long trainingParticipantId;
  /**
   * 被阻塞的计划内容 ID；计划级问题时为空。
   */
  private Long planContentId;
  /**
   * 稳定原因码。
   */
  private String reasonCode;
  /**
   * 中文原因说明。
   */
  private String message;
}

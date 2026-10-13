package com.sts.training.port;

import lombok.Data;

/**
 * 培训参训人与课程/考试执行的绑定参数。
 */
@Data
public class TrainingParticipantBinding {
  /**
   * 培训参训人 ID。
   */
  private Long trainingParticipantId;
  /**
   * 学员用户 ID。
   */
  private Long userId;
}

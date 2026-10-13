package com.sts.training.port;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 课程域返回给培训的报名结果投影。
 */
@Data
public class TrainingCourseResult {
  /**
   * 课程开课 ID。
   */
  private Long courseOfferingId;
  /**
   * 课程报名 ID。
   */
  private Long sourceRecordId;
  /**
   * 学习状态。
   */
  private String sourceStatus;
  /**
   * 正式成绩；未完成时为空。
   */
  private BigDecimal score;
  /**
   * 累计学习秒数。
   */
  private Integer studySeconds;
  /**
   * 完成时间。
   */
  private LocalDateTime completedTime;
}

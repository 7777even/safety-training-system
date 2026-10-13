package com.sts.training.port;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 考试域返回给培训的 Candidate/Attempt 结果投影。
 */
@Data
public class TrainingAssessmentResult {
  /**
   * 考核执行 ID。
   */
  private Long assessmentExecutionId;
  /**
   * 考试人员 ID。
   */
  private Long sourceRecordId;
  /**
   * 人员状态。
   */
  private String sourceStatus;
  /**
   * 已发布成绩；未发布时为空。
   */
  private BigDecimal score;
  /**
   * 最终作答尝试 ID。
   */
  private Long finalAttemptId;
  /**
   * 可自动判断的题目数。
   */
  private Integer evaluatedCount;
  /**
   * 自动判断正确数。
   */
  private Integer correctCount;
  /**
   * 自动判断错误数。
   */
  private Integer wrongCount;
  /**
   * 只记录作答、不判断对错的题目数。
   */
  private Integer unassessedCount;
  /**
   * 仅以可判断题为分母的正确率；无可判断题时为空。
   */
  private BigDecimal accuracyRate;
  /**
   * 成绩发布时间。
   */
  private LocalDateTime completedTime;
}

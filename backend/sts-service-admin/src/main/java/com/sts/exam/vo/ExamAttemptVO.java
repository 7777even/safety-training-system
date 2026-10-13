package com.sts.exam.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class ExamAttemptVO {
  private Long examAttemptId;
  private Long assessmentExecutionId;
  private String sourceType;
  private String assessmentKind;
  private String resourceType;
  private Long resourceId;
  private String resourceName;
  private Long examId;
  private Long examCandidateId;
  private Long userId;
  private Long paperVersionId;
  private Integer attemptNo;
  private String attemptType;
  private String attemptStatus;
  private LocalDateTime startedTime;
  private LocalDateTime deadlineTime;
  private LocalDateTime submittedTime;
  private Integer usedSeconds;
  private BigDecimal objectiveScore;
  private BigDecimal subjectiveScore;
  private BigDecimal finalScore;
  private Integer isPassed;
  /** 管理端发布前核对使用的服务端派生预计及格结果；待人工批阅时为空。 */
  private Integer predictedIsPassed;
  private Integer correctCount;
  private Integer wrongCount;
  private Integer evaluatedCount;
  private Integer unassessedCount;
  private BigDecimal accuracyRate;
  /**
   * 题库随机种子，仅用于审计和复现。
   */
  private Long drawSeed;
  private String clientAttemptToken;
  private Integer version;
  private String scorePublishStatus;
  private LocalDateTime scorePublishedTime;
  private LocalDateTime resultEffectiveTime;
  private Boolean scoreVisible;
  /** 冻结的考试防作弊事件审计开关，仅独立考试答题端消费。 */
  private Integer isAnticheatEnabled;
  /** 冻结的答题页水印开关，仅独立考试答题端消费。 */
  private Integer isWatermarkEnabled;
  /** 当前考生的补考资格，仅管理端作答详情用于展示，不替代创建补考时的二次校验。 */
  private ExamRetakeEligibilityVO retakeEligibility;
  private List<ExamAttemptQuestionVO> questions;
}

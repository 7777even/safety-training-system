package com.sts.exam.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sts.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sts_exam_attempt")
public class ExamAttempt extends BaseEntity {
  @TableId(type = IdType.AUTO)
  private Long examAttemptId;
  private Long organizationId;
  private Long assessmentExecutionId;
  private String sourceType;
  private String assessmentKind;
  private Long examCandidateId;
  private Long userId;
  private Long paperVersionId;
  private String paperSourceType;
  private Long paperSourceId;
  private BigDecimal totalScoreSnapshot;
  private BigDecimal passScoreSnapshot;
  private Integer durationSecondsSnapshot;
  private String questionOrderModeSnapshot;
  private String multiSelectScoringSnapshot;
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
  private Integer correctCount;
  private Integer wrongCount;
  /**
   * 新版不评分练习中参与对错判断的题数。
   */
  private Integer evaluatedCount;
  /**
   * 新版不评分练习中只记录作答、不判断对错的题数。
   */
  private Integer unassessedCount;
  /**
   * 仅以可判断题为分母的正确率；无可判断题时为空。
   */
  private BigDecimal accuracyRate;
  private String submitReason;
  private String platform;
  private String operatingSystem;
  private String browser;
  private String clientIp;
  private LocalDateTime reviewCompletedTime;
  private LocalDateTime scorePublishedTime;
  private LocalDateTime resultEffectiveTime;
  private String clientAttemptToken;
  /**
   * 本次题库随机抽取的服务端种子，可用于复现冻结结果。
   */
  private Long drawSeed;
}

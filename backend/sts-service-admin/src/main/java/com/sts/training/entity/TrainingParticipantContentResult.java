package com.sts.training.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sts.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 培训参训人逐内容结果投影。业务真相源仍是课程/考试/线下事实表。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sts_training_participant_content_result")
public class TrainingParticipantContentResult extends BaseEntity {
  @TableId(type = IdType.AUTO)
  private Long trainingParticipantContentResultId;
  private Long organizationId;
  private Long trainingPlanId;
  private Long trainingParticipantId;
  private Long planContentId;
  private String contentType;
  private Long executionId;
  private String sourceRecordType;
  private Long sourceRecordId;
  private Long finalAttemptId;
  /**
   * 题库练习反馈摘要；非题库内容或历史结果为空。
   */
  private Integer evaluatedCount;
  private Integer correctCount;
  private Integer wrongCount;
  private Integer unassessedCount;
  private BigDecimal accuracyRate;
  private String sourceStatus;
  private String resultStatus;
  /**
   * 该项获得学时秒数。课程为学习秒，线下为合格成绩对应的安排学时秒，考试/题库为 0。
   */
  private Integer effectiveDurationSeconds;
  private BigDecimal score;
  private LocalDateTime completedTime;
  private LocalDateTime sourceUpdatedTime;
  private LocalDateTime synchronizedTime;
  private Integer resultRevision;
  private LocalDateTime finalizedTime;
}

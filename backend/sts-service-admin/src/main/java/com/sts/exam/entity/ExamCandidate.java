package com.sts.exam.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.sts.common.entity.BaseEntity;
import java.time.LocalDateTime;
import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sts_exam_candidate")
public class ExamCandidate extends BaseEntity {
  @TableId(type = IdType.AUTO)
  private Long examCandidateId;
  private Long organizationId;
  private Long assessmentExecutionId;
  private Long userId;
  private Long courseOfferingId;
  private Long courseItemId;
  private Long courseEnrollmentId;
  private Long trainingParticipantId;
  private String assignmentSource;
  private Long sourceBusinessId;
  private String candidateProfileSnapshotJson;
  private LocalDateTime profileSnapshotTime;
  private LocalDateTime noticeConfirmedTime;
  private String candidateStatus;
  private Integer attemptCount;
  private BigDecimal bestScore;
  private BigDecimal finalScore;
  private Long finalAttemptId;
  private LocalDateTime resultPublishedTime;
  private LocalDateTime resultEffectiveTime;
}

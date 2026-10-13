package com.sts.course.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class CourseAssessmentResultVO {
  private Long courseAssessmentResultId;
  private Long assessmentExecutionId;
  private Long examCandidateId;
  private Long examAttemptId;
  private Long courseEnrollmentId;
  private Long courseOfferingId;
  private Long courseItemId;
  private Long userId;
  /** 学员姓名快照，用于课程考核管理结果列表展示。 */
  private String learnerName;
  private String sourceType;
  private Integer attemptNo;
  private BigDecimal finalScore;
  private Integer isPassed;
  private Integer correctCount;
  private Integer wrongCount;
  /** 已判题正确率百分比；无已判题为空。 */
  private BigDecimal accuracyPercent;
  private LocalDateTime effectiveTime;
}

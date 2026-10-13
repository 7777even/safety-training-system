package com.sts.course.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 考试执行域向课程域传递的已生效考核结果事实。
 */
public record CourseAssessmentResultEffective(
  Long organizationId,
  Long assessmentExecutionId,
  Long examAttemptId,
  Long examCandidateId,
  Long userId,
  Long courseEnrollmentId,
  Long courseOfferingId,
  Long courseItemId,
  String sourceType,
  Integer attemptNo,
  LocalDateTime effectiveTime,
  BigDecimal finalScore,
  Integer isPassed,
  Integer correctCount,
  Integer wrongCount) {
}

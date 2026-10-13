package com.sts.course.dto;

import java.time.LocalDateTime;

/**
 * 考试执行域向课程域传递的课程测验交卷事实。
 *
 * <p>只承载“学员已参加、等待批阅”的参与事实，不承载成绩；成绩生效仍走
 * {@link CourseAssessmentResultEffective}，两者按同一报名内容项幂等收敛。</p>
 */
public record CourseAssessmentSubmitted(
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
  LocalDateTime submittedTime) {
}

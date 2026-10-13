package com.sts.exam.service;

import com.sts.course.entity.CourseOffering;
import com.sts.course.snapshot.CourseContentSnapshot;
import com.sts.exam.entity.AssessmentExecution;
import com.sts.exam.entity.Exam;

import java.util.List;

public interface AssessmentExecutionService {
  AssessmentExecution ensureStandalone(Exam exam);

  AssessmentExecution refreshStandalone(Exam exam);

  /**
   * 按考试字段物化或刷新考核执行。extraPolicy 用于培训指纹与抽题规则等覆盖项，独立考试传 null。
   */
  AssessmentExecution refreshStandalone(Exam exam, java.util.Map<String, Object> extraPolicy);

  AssessmentExecution findStandaloneByExamId(Long examId);

  void deleteStandaloneIfUnused(Long examId);

  List<AssessmentExecution> ensureCourseExecutions(CourseOffering offering, CourseContentSnapshot snapshot);

  AssessmentExecution requireById(Long assessmentExecutionId);

  AssessmentExecution requireStandaloneByExamId(Long examId);

  AssessmentExecution findCourse(Long courseOfferingId, Long courseVersionId, Long courseItemId);

  AssessmentExecution requireCourse(Long courseOfferingId, Long courseVersionId, Long courseItemId);
}

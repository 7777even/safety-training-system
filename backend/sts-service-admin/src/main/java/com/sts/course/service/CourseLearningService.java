package com.sts.course.service;

import com.sts.common.api.PageRequest;
import com.sts.common.api.PageResult;
import com.sts.course.dto.*;
import com.sts.course.vo.CourseEnrollmentDetailVO;
import com.sts.course.vo.CourseEvaluationListItemVO;
import com.sts.course.vo.LearnerCourseEnrollmentVO;
import com.sts.course.vo.LearnerCourseOfferingVO;
import com.sts.course.vo.LearnerCourseTasksVO;

public interface CourseLearningService {
  /** 学员只读分类树，不授予后台分类管理权限。 */
  java.util.List<com.sts.course.vo.CourseCategoryTreeVO> learnerCategories();
  /** 结业开考前校验本人已完成其他必需学习内容。 */
  void assertFinalAssessmentReady(Long enrollmentId, Long itemId, Long userId);

  /** 本人直播参与成功即完成，不计观看时长。 */
  com.sts.course.vo.LiveParticipationVO participateLive(Long enrollmentId, Long itemId);

  Long reportSession(StudySessionReportRequest request);

  PageResult<LearnerCourseEnrollmentVO> pageMyEnrollments(
    PageRequest<LearnerCourseEnrollmentQuery> request);

  PageResult<LearnerCourseOfferingVO> pageLearnerOfferings(
    PageRequest<LearnerCourseOfferingQuery> request);

  LearnerCourseTasksVO getMyEnrollmentTasks(Long enrollmentId);

  CourseEnrollmentDetailVO getMyEnrollmentDetail(Long enrollmentId);

  /** 按本人有效报名分页读取同次开课的学员评价。 */
  PageResult<CourseEvaluationListItemVO> pagePeerEvaluations(Long enrollmentId, PageRequest<Void> request);

  CourseEnrollmentDetailVO getLearningDetail(Long enrollmentId);

  void submitEvaluation(Long enrollmentId, CourseEvaluationSubmitRequest request);

  void applyEffectiveAssessmentResult(CourseAssessmentResultEffective result);

  /**
   * 课程测验交卷即投影内容项参与事实：内容项转为学习中并记一次作答，
   * 成绩生效后才由 {@link #applyEffectiveAssessmentResult} 转为已完成。
   */
  void applySubmittedAssessmentResult(CourseAssessmentSubmitted result);

}

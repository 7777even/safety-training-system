package com.sts.course.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.sts.common.api.PageRequest;
import com.sts.common.api.PageResult;
import com.sts.course.dto.*;
import com.sts.course.service.CourseLearningService;
import com.sts.course.service.CourseAssessmentAttemptService;
import com.sts.course.vo.CourseEnrollmentDetailVO;
import com.sts.course.vo.CourseEvaluationListItemVO;
import com.sts.course.vo.LearnerCourseEnrollmentVO;
import com.sts.course.vo.LearnerCourseOfferingVO;
import com.sts.course.vo.LearnerCourseTasksVO;
import com.sts.exam.vo.ExamAttemptVO;
import com.sts.exam.dto.SaveAnswersRequest;
import com.sts.exam.dto.SubmitAttemptRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 学员课程列表、学习任务及评价提交接口。
 */
@RestController
@RequiredArgsConstructor
public class CourseLearningController {

  private final CourseLearningService courseLearningService;
  private final CourseAssessmentAttemptService courseAssessmentAttemptService;

  /** 学员读取当前租户启用的课程分类树。 */
  @GetMapping("/course/learning/categories/tree")
  public java.util.List<com.sts.course.vo.CourseCategoryTreeVO> learnerCategories() {
    return courseLearningService.learnerCategories();
  }

  /**
   * 分页查询我的课程参与记录。
   */
  @GetMapping("/course/learning/my-enrollments")
  public PageResult<LearnerCourseEnrollmentVO> pageMyEnrollments(
    PageRequest<LearnerCourseEnrollmentQuery> request) {
    return courseLearningService.pageMyEnrollments(request);
  }

  /**
   * 分页查询学员可参加的开班。
   */
  @GetMapping("/course/learning/offerings")
  public PageResult<LearnerCourseOfferingVO> pageLearnerOfferings(
    PageRequest<LearnerCourseOfferingQuery> request) {
    return courseLearningService.pageLearnerOfferings(request);
  }

  /**
   * 查询我的课程任务。
   */
  @GetMapping("/course/learning/enrollments/{enrollmentId}/tasks")
  public LearnerCourseTasksVO getMyEnrollmentTasks(@PathVariable Long enrollmentId) {
    return courseLearningService.getMyEnrollmentTasks(enrollmentId);
  }

  /**
   * 从本人有效课程报名启动理论考核。
   */
  @PostMapping("/course/learning/enrollments/{enrollmentId}/items/{itemId}/attempts")
  public ExamAttemptVO startAssessment(@PathVariable Long enrollmentId, @PathVariable Long itemId,
                                       @RequestBody @Valid CourseAssessmentStartRequest request) {
    return courseAssessmentAttemptService.start(enrollmentId, itemId, request);
  }

  /**
   * 查询本人课程理论考核作答详情。
   */
  @GetMapping("/course/learning/enrollments/{enrollmentId}/items/{itemId}/attempts/{attemptId}")
  public ExamAttemptVO getAssessment(@PathVariable Long enrollmentId, @PathVariable Long itemId,
                                     @PathVariable Long attemptId) {
    return courseAssessmentAttemptService.get(enrollmentId, itemId, attemptId);
  }

  /** 暂存本人课程理论考核答案。 */
  @PutMapping("/course/learning/enrollments/{enrollmentId}/items/{itemId}/attempts/{attemptId}/answers")
  public void saveAssessmentAnswers(@PathVariable Long enrollmentId, @PathVariable Long itemId,
                                    @PathVariable Long attemptId,
                                    @RequestBody @Valid SaveAnswersRequest request) {
    courseAssessmentAttemptService.saveAnswers(enrollmentId, itemId, attemptId, request);
  }

  /** 提交本人课程理论考核，成绩按课程规则自动生效。 */
  @PostMapping("/course/learning/enrollments/{enrollmentId}/items/{itemId}/attempts/{attemptId}/submit")
  public ExamAttemptVO submitAssessment(@PathVariable Long enrollmentId, @PathVariable Long itemId,
                                        @PathVariable Long attemptId,
                                        @RequestBody SubmitAttemptRequest request) {
    return courseAssessmentAttemptService.submit(enrollmentId, itemId, attemptId, request);
  }

  /** 登记本人直播参与，返回合法外部地址。 */
  @PostMapping("/course/learning/enrollments/{enrollmentId}/items/{itemId}/live-participation")
  public com.sts.course.vo.LiveParticipationVO participateLive(@PathVariable Long enrollmentId, @PathVariable Long itemId) {
    return courseLearningService.participateLive(enrollmentId, itemId);
  }

  /**
   * 上报并保存学习会话进度.
   */
  @PostMapping("/course/learning/sessions")
  public Long reportSession(@RequestBody @Valid StudySessionReportRequest request) {
    return courseLearningService.reportSession(request);
  }

  /**
   * 查询我的课程参与详情。
   */
  @GetMapping("/course/learning/enrollments/{enrollmentId}")
  public CourseEnrollmentDetailVO getMyEnrollmentDetail(@PathVariable Long enrollmentId) {
    return courseLearningService.getMyEnrollmentDetail(enrollmentId);
  }

  /** 本人有效报名可分页查看同次开课评价，匿名身份由服务端裁剪。 */
  @GetMapping("/course/learning/enrollments/{enrollmentId}/evaluations")
  public PageResult<CourseEvaluationListItemVO> pagePeerEvaluations(
    @PathVariable Long enrollmentId, PageRequest<Void> request) {
    return courseLearningService.pagePeerEvaluations(enrollmentId, request);
  }

  /**
   * 提交课程学习评价。
   */
  @PostMapping("/course/learning/enrollments/{enrollmentId}/evaluation")
  public void submitEvaluation(@PathVariable Long enrollmentId,
                               @RequestBody @Valid CourseEvaluationSubmitRequest request) {
    courseLearningService.submitEvaluation(enrollmentId, request);
  }

  /**
   * 查询课程学习详情。
   */
  @GetMapping("/course/learning/admin/enrollments/{enrollmentId}")
  @SaCheckPermission("course:learning:view")
  public CourseEnrollmentDetailVO getLearningDetail(@PathVariable Long enrollmentId) {
    return courseLearningService.getLearningDetail(enrollmentId);
  }
}

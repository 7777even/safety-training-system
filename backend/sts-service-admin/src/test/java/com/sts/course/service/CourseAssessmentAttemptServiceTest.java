package com.sts.course.service;

import cn.dev33.satoken.stp.StpUtil;
import com.sts.course.dto.CourseAssessmentStartRequest;
import com.sts.course.entity.CourseEnrollment;
import com.sts.course.entity.CourseOffering;
import com.sts.course.mapper.CourseEnrollmentMapper;
import com.sts.course.mapper.CourseOfferingMapper;
import com.sts.exam.dto.SaveAnswersRequest;
import com.sts.exam.entity.AssessmentExecution;
import com.sts.exam.service.AssessmentExecutionService;
import com.sts.exam.service.ExamAttemptService;
import com.sts.exam.service.ExamLearnerService;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

class CourseAssessmentAttemptServiceTest {

  @Test
  void saveAnswers_rejectsAttemptFromAnotherCourseExecution() {
    CourseEnrollmentMapper enrollmentMapper = mock(CourseEnrollmentMapper.class);
    CourseOfferingMapper offeringMapper = mock(CourseOfferingMapper.class);
    AssessmentExecutionService executionService = mock(AssessmentExecutionService.class);
    ExamAttemptService attemptService = mock(ExamAttemptService.class);
    ExamLearnerService learnerService = mock(ExamLearnerService.class);
    CourseEnrollment enrollment = new CourseEnrollment();
    enrollment.setCourseOfferingId(10L);
    enrollment.setUserId(7L);
    enrollment.setLearningStatus("LEARNING");
    when(enrollmentMapper.selectById(100L)).thenReturn(enrollment);
    CourseOffering offering = new CourseOffering();
    offering.setCourseOfferingId(10L);
    offering.setCourseVersionId(20L);
    offering.setPublishStatus("PUBLISHED");
    when(offeringMapper.selectById(10L)).thenReturn(offering);
    AssessmentExecution execution = new AssessmentExecution();
    execution.setAssessmentExecutionId(30L);
    when(executionService.requireCourse(10L, 20L, 40L)).thenReturn(execution);
    com.sts.exam.vo.ExamAttemptVO foreignAttempt = new com.sts.exam.vo.ExamAttemptVO();
    foreignAttempt.setAssessmentExecutionId(31L);
    when(learnerService.getMyAttempt(50L)).thenReturn(foreignAttempt);
    CourseAssessmentAttemptService service = new CourseAssessmentAttemptService(
      enrollmentMapper, offeringMapper, executionService, attemptService, learnerService);

    try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
      stp.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
      org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.saveAnswers(100L, 40L, 50L, new SaveAnswersRequest()))
        .isInstanceOf(com.sts.common.exception.BusinessException.class)
        .hasMessage("作答不属于指定课程内容项");
    }

    verifyNoInteractions(attemptService);
  }

  @Test
  void start_derivesUserAndExecutionOnlyFromCurrentEnrollment() {
    CourseEnrollmentMapper enrollmentMapper = mock(CourseEnrollmentMapper.class);
    CourseOfferingMapper offeringMapper = mock(CourseOfferingMapper.class);
    AssessmentExecutionService executionService = mock(AssessmentExecutionService.class);
    ExamAttemptService attemptService = mock(ExamAttemptService.class);
    ExamLearnerService learnerService = mock(ExamLearnerService.class);
    CourseEnrollment enrollment = new CourseEnrollment();
    enrollment.setCourseEnrollmentId(100L);
    enrollment.setCourseOfferingId(10L);
    enrollment.setUserId(7L);
    enrollment.setLearningStatus("LEARNING");
    when(enrollmentMapper.selectById(100L)).thenReturn(enrollment);
    CourseOffering offering = new CourseOffering();
    offering.setCourseOfferingId(10L);
    offering.setCourseVersionId(20L);
    offering.setPublishStatus("PUBLISHED");
    when(offeringMapper.selectById(10L)).thenReturn(offering);
    AssessmentExecution execution = new AssessmentExecution();
    execution.setAssessmentExecutionId(30L);
    execution.setAssessmentKind("QUIZ");
    when(executionService.requireCourse(10L, 20L, 40L)).thenReturn(execution);
    CourseAssessmentAttemptService service = new CourseAssessmentAttemptService(
      enrollmentMapper, offeringMapper, executionService, attemptService, learnerService);

    CourseAssessmentStartRequest request = new CourseAssessmentStartRequest();
    request.setClientAttemptToken("course-token");
    try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
      stp.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
      service.start(100L, 40L, request);
    }

    verify(attemptService).start(argThat(start ->
                                           start.getAssessmentExecutionId().equals(30L)
                                             && start.getExamId() == null
                                             && start.getUserId().equals(7L)
                                             && start.getCourseEnrollmentId().equals(100L)
                                             && start.getCourseItemId().equals(40L)
                                             && start.getAttemptType() == null));
    assertThat(request.getClientAttemptToken()).isEqualTo("course-token");
  }

  @Test
  void start_doesNotDeclareAttemptType() {
    CourseEnrollmentMapper enrollmentMapper = mock(CourseEnrollmentMapper.class);
    CourseOfferingMapper offeringMapper = mock(CourseOfferingMapper.class);
    AssessmentExecutionService executionService = mock(AssessmentExecutionService.class);
    ExamAttemptService attemptService = mock(ExamAttemptService.class);
    CourseEnrollment enrollment = new CourseEnrollment();
    enrollment.setCourseEnrollmentId(100L);
    enrollment.setCourseOfferingId(10L);
    enrollment.setUserId(7L);
    enrollment.setLearningStatus("LEARNING");
    when(enrollmentMapper.selectById(100L)).thenReturn(enrollment);
    CourseOffering offering = new CourseOffering();
    offering.setCourseOfferingId(10L);
    offering.setCourseVersionId(20L);
    offering.setPublishStatus("PUBLISHED");
    when(offeringMapper.selectById(10L)).thenReturn(offering);
    AssessmentExecution execution = new AssessmentExecution();
    execution.setAssessmentExecutionId(30L);
    execution.setAssessmentKind("BANK_PRACTICE");
    when(executionService.requireCourse(10L, 20L, 40L)).thenReturn(execution);
    CourseAssessmentAttemptService service = new CourseAssessmentAttemptService(
      enrollmentMapper, offeringMapper, executionService, attemptService, mock(ExamLearnerService.class));

    try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
      stp.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
      service.start(100L, 40L, new CourseAssessmentStartRequest());
    }

    verify(attemptService).start(argThat(start -> start.getAttemptType() == null));
  }
}

package com.sts.course.service;

import cn.dev33.satoken.stp.StpUtil;
import com.sts.common.exception.BusinessException;
import com.sts.course.dto.CourseAssessmentStartRequest;
import com.sts.course.entity.CourseEnrollment;
import com.sts.course.entity.CourseOffering;
import com.sts.course.mapper.CourseEnrollmentMapper;
import com.sts.course.mapper.CourseOfferingMapper;
import com.sts.exam.dto.SaveAnswersRequest;
import com.sts.exam.dto.StartAttemptRequest;
import com.sts.exam.dto.SubmitAttemptRequest;
import com.sts.exam.service.AssessmentExecutionService;
import com.sts.exam.service.ExamAttemptService;
import com.sts.exam.service.ExamLearnerService;
import com.sts.exam.vo.ExamAttemptVO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CourseAssessmentAttemptService {
  private final CourseEnrollmentMapper enrollmentMapper;
  private final CourseOfferingMapper offeringMapper;
  private final AssessmentExecutionService executionService;
  private final ExamAttemptService attemptService;
  private final ExamLearnerService learnerService;

  public ExamAttemptVO start(Long enrollmentId, Long itemId, CourseAssessmentStartRequest request) {
    CourseAssessmentContext context = requireContext(enrollmentId, itemId);
    Long userId = context.enrollment().getUserId();
    CourseOffering offering = context.offering();
    var execution = context.execution();
    StartAttemptRequest start = new StartAttemptRequest();
    start.setAssessmentExecutionId(execution.getAssessmentExecutionId());
    start.setUserId(userId);
    start.setCourseEnrollmentId(enrollmentId);
    start.setCourseOfferingId(offering.getCourseOfferingId());
    start.setCourseItemId(itemId);
    start.setClientAttemptToken(request.getClientAttemptToken());
    start.setPlatform(request.getPlatform());
    start.setOperatingSystem(request.getOperatingSystem());
    start.setBrowser(request.getBrowser());
    start.setClientIp(request.getClientIp());
    return attemptService.start(start);
  }

  public ExamAttemptVO get(Long enrollmentId, Long itemId, Long attemptId) {
    return requireOwnedAttempt(enrollmentId, itemId, attemptId);
  }

  /** 暂存课程内容项中的作答，先校验课程归属再委托考试作答服务。 */
  public void saveAnswers(Long enrollmentId, Long itemId, Long attemptId, SaveAnswersRequest request) {
    requireOwnedAttempt(enrollmentId, itemId, attemptId);
    attemptService.saveAnswers(attemptId, request);
  }

  /** 提交课程内容项中的作答，禁止绕过课程上下文调用独立考试入口。 */
  public ExamAttemptVO submit(Long enrollmentId, Long itemId, Long attemptId, SubmitAttemptRequest request) {
    requireOwnedAttempt(enrollmentId, itemId, attemptId);
    return attemptService.submit(attemptId, request);
  }

  /** 校验作答属于当前学员报名的指定课程内容项。 */
  private ExamAttemptVO requireOwnedAttempt(Long enrollmentId, Long itemId, Long attemptId) {
    CourseAssessmentContext context = requireContext(enrollmentId, itemId);
    ExamAttemptVO attempt = learnerService.getMyAttempt(attemptId);
    if (!context.execution().getAssessmentExecutionId().equals(attempt.getAssessmentExecutionId())) {
      throw new BusinessException(HttpStatus.FORBIDDEN, 5081, "作答不属于指定课程内容项");
    }
    return attempt;
  }

  private CourseAssessmentContext requireContext(Long enrollmentId, Long itemId) {
    Long userId = StpUtil.getLoginIdAsLong();
    CourseEnrollment enrollment = enrollmentMapper.selectById(enrollmentId);
    if (enrollment == null || !userId.equals(enrollment.getUserId())
      || !java.util.Set.of("NOT_STARTED", "LEARNING", "COMPLETED").contains(enrollment.getLearningStatus())) {
      throw new BusinessException(HttpStatus.FORBIDDEN, 5081, "无权访问课程报名");
    }
    CourseOffering offering = offeringMapper.selectById(enrollment.getCourseOfferingId());
    if (offering == null || offering.getCourseVersionId() == null
      || !("PUBLISHED".equals(offering.getPublishStatus()) || "ENDED".equals(offering.getPublishStatus()))) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 5083, "开课没有可执行的课程版本");
    }
    var execution = executionService.requireCourse(offering.getCourseOfferingId(),
                                                   offering.getCourseVersionId(), itemId);
    return new CourseAssessmentContext(enrollment, offering, execution);
  }

  private record CourseAssessmentContext(CourseEnrollment enrollment, CourseOffering offering,
                                         com.sts.exam.entity.AssessmentExecution execution) {
  }
}

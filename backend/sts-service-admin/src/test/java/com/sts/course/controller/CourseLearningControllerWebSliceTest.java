package com.sts.course.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sts.admin.display.advice.ResultEasyTransAdvice;
import com.sts.common.api.PageRequestArgumentResolver;
import com.sts.common.api.PageResult;
import com.sts.common.exception.BusinessException;
import com.sts.common.exception.HttpApiExceptionHandler;
import com.sts.course.dto.StudySessionReportRequest;
import com.sts.exam.dto.SaveAnswersRequest;
import com.sts.exam.dto.SubmitAttemptRequest;
import com.sts.course.service.CourseLearningService;
import com.sts.course.service.CourseAssessmentAttemptService;
import com.sts.exam.vo.ExamAttemptVO;
import com.sts.course.vo.*;
import io.github.easytrans.core.spi.TranslationExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CourseLearningControllerWebSliceTest {

  private CourseLearningService courseLearningService;
  private CourseAssessmentAttemptService courseAssessmentAttemptService;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    courseLearningService = mock(CourseLearningService.class);
    courseAssessmentAttemptService = mock(CourseAssessmentAttemptService.class);
    TranslationExecutor translationExecutor = mock(TranslationExecutor.class);
    mockMvc = MockMvcBuilders.standaloneSetup(
        new CourseLearningController(courseLearningService, courseAssessmentAttemptService))
      .setCustomArgumentResolvers(new PageRequestArgumentResolver())
      .setControllerAdvice(
        new HttpApiExceptionHandler(),
        new ResultEasyTransAdvice(translationExecutor, new ObjectMapper()))
      .build();
  }

  @Test
  @DisplayName("课程考核开考只接收客户端令牌并返回执行来源")
  void startAssessment_isWrapped() throws Exception {
    ExamAttemptVO attempt = new ExamAttemptVO();
    attempt.setExamAttemptId(5001L);
    attempt.setAssessmentExecutionId(3001L);
    attempt.setSourceType("COURSE_BANK_PRACTICE");
    when(courseAssessmentAttemptService.start(eq(1000L), eq(102L), any())).thenReturn(attempt);

    mockMvc.perform(post("/course/learning/enrollments/1000/items/102/attempts")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content("{\"clientAttemptToken\":\"course-token\"}"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.examAttemptId").value(5001))
      .andExpect(jsonPath("$.data.assessmentExecutionId").value(3001))
      .andExpect(jsonPath("$.data.sourceType").value("COURSE_BANK_PRACTICE"));
  }

  @Test
  @DisplayName("课程考核详情按报名和内容项路径返回")
  void getAssessment_isWrapped() throws Exception {
    ExamAttemptVO attempt = new ExamAttemptVO();
    attempt.setExamAttemptId(5001L);
    attempt.setAssessmentExecutionId(3001L);
    when(courseAssessmentAttemptService.get(1000L, 102L, 5001L)).thenReturn(attempt);

    mockMvc.perform(get("/course/learning/enrollments/1000/items/102/attempts/5001"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.examAttemptId").value(5001))
      .andExpect(jsonPath("$.data.assessmentExecutionId").value(3001));
  }

  @Test
  @DisplayName("课程考核暂存答案必须携带课程报名与内容项路径")
  void saveAssessmentAnswers_isWrapped() throws Exception {
    doNothing().when(courseAssessmentAttemptService).saveAnswers(eq(1000L), eq(102L), eq(5001L), any());

    mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                        "/course/learning/enrollments/1000/items/102/attempts/5001/answers")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content("{\"answers\":[{\"attemptQuestionId\":9001,\"answerText\":\"A\",\"items\":[]}]}"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.code").value(0));

    verify(courseAssessmentAttemptService).saveAnswers(eq(1000L), eq(102L), eq(5001L), any(SaveAnswersRequest.class));
  }

  @Test
  @DisplayName("课程考核交卷必须走课程内容项路径")
  void submitAssessment_isWrapped() throws Exception {
    ExamAttemptVO attempt = new ExamAttemptVO();
    attempt.setExamAttemptId(5001L);
    when(courseAssessmentAttemptService.submit(eq(1000L), eq(102L), eq(5001L), any())).thenReturn(attempt);

    mockMvc.perform(post("/course/learning/enrollments/1000/items/102/attempts/5001/submit")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content("{\"submitReason\":\"MANUAL\",\"usedSeconds\":30}"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.examAttemptId").value(5001));

    verify(courseAssessmentAttemptService).submit(eq(1000L), eq(102L), eq(5001L), any(SubmitAttemptRequest.class));
  }

  @Test
  @DisplayName("学员课程入口分页结果被 Result 包装")
  void learnerCourseEntries_areWrapped() throws Exception {
    LearnerCourseEnrollmentVO enrollment = new LearnerCourseEnrollmentVO();
    enrollment.setEnrollmentId(1000L);
    enrollment.setCourseName("安全生产基础");
    when(courseLearningService.pageMyEnrollments(any()))
      .thenReturn(new PageResult<>(List.of(enrollment), 1L));

    LearnerCourseOfferingVO offering = new LearnerCourseOfferingVO();
    offering.setOfferingId(100L);
    offering.setEnrollmentAvailability("AVAILABLE");
    when(courseLearningService.pageLearnerOfferings(any()))
      .thenReturn(new PageResult<>(List.of(offering), 1L));

    mockMvc.perform(get("/course/learning/my-enrollments")
                      .param("pageNum", "2")
                      .param("pageSize", "10")
                      .param("query.learningStatus", "LEARNING"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.list[0].enrollmentId").value(1000))
      .andExpect(jsonPath("$.data.list[0].courseName").value("安全生产基础"));

    mockMvc.perform(get("/course/learning/offerings")
                      .param("query.keyword", "安全")
                      .param("query.categoryId", "10")
                      .param("query.offeringStatus", "AVAILABLE"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.list[0].offeringId").value(100))
      .andExpect(jsonPath("$.data.list[0].enrollmentAvailability").value("AVAILABLE"));
  }

  @Test
  @DisplayName("本人学习任务被 Result 包装")
  void learnerTasks_areWrapped() throws Exception {
    LearnerCourseTasksVO vo = new LearnerCourseTasksVO();
    vo.setEnrollmentId(1000L);
    vo.setCourseName("安全生产基础");
    when(courseLearningService.getMyEnrollmentTasks(1000L)).thenReturn(vo);

    mockMvc.perform(get("/course/learning/enrollments/1000/tasks"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.enrollmentId").value(1000))
      .andExpect(jsonPath("$.data.courseName").value("安全生产基础"));
  }

  @Test
  @DisplayName("reportSession 成功返回被 Result 包装的会话 ID")
  void reportSession_success_isWrapped() throws Exception {
    when(courseLearningService.reportSession(any(StudySessionReportRequest.class))).thenReturn(500L);

    mockMvc.perform(post("/course/learning/sessions")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content("""
                                 {"enrollmentId":1000,"courseItemId":1,"clientTraceId":"trace-1",
                                 "deviceType":"PC","deviceId":"device-web-001","startedTime":"2026-07-22T10:00:00",
                                 "validStudySeconds":60,"endReason":"NORMAL"}
                                 """))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.code").value(0))
      .andExpect(jsonPath("$.data").value(500));
  }

  @Test
  @DisplayName("同课评价列表：分页参数透传并由 Result 包装")
  void pagePeerEvaluations_isWrapped() throws Exception {
    CourseEvaluationListItemVO item = new CourseEvaluationListItemVO();
    item.setDisplayName("匿名学员");
    item.setIsAnonymous(1);
    item.setSubmittedTime("2026-09-29");
    when(courseLearningService.pagePeerEvaluations(eq(1000L), any()))
      .thenReturn(new PageResult<>(List.of(item), 1));

    mockMvc.perform(get("/course/learning/enrollments/1000/evaluations")
        .param("pageNum", "2").param("pageSize", "10"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.total").value(1))
      .andExpect(jsonPath("$.data.list[0].displayName").value("匿名学员"))
      .andExpect(jsonPath("$.data.list[0].submittedTime").value("2026-09-29"))
      .andExpect(jsonPath("$.data.list[0].userId").doesNotExist())
      .andExpect(jsonPath("$.data.list[0].courseEnrollmentId").doesNotExist());
  }

  @Test
  @DisplayName("getMyEnrollmentDetail 详情被 Result 包装")
  void getMyEnrollmentDetail_isWrapped() throws Exception {
    CourseEnrollmentDetailVO vo = new CourseEnrollmentDetailVO();
    vo.setCourseEnrollmentId(1000L);
    vo.setLearningStatus("LEARNING");
    CourseLearningCourseVO course = new CourseLearningCourseVO();
    course.setCourseId(10L);
    course.setCourseName("消防安全教育");
    LearnerCourseDownloadAssetVO asset = new LearnerCourseDownloadAssetVO();
    asset.setAssetId(30L);
    asset.setDownloadable(true);
    asset.setDownloadUrl("/api/course/learning/enrollments/1000/download-assets/30/file");
    vo.setCourse(course);
    vo.setDownloadAssets(List.of(asset));
    when(courseLearningService.getMyEnrollmentDetail(1000L)).thenReturn(vo);

    mockMvc.perform(get("/course/learning/enrollments/1000"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.code").value(0))
      .andExpect(jsonPath("$.data.learningStatus").value("LEARNING"))
      .andExpect(jsonPath("$.data.course.courseName").value("消防安全教育"))
      .andExpect(jsonPath("$.data.downloadAssets[0].assetId").value(30))
      .andExpect(jsonPath("$.data.downloadAssets[0].downloadable").value(true));
  }

  @Test
  @DisplayName("reportSession 业务异常 → 403")
  void reportSession_businessException_yields403() throws Exception {
    when(courseLearningService.reportSession(any(StudySessionReportRequest.class)))
      .thenThrow(new BusinessException(HttpStatus.FORBIDDEN, 5075, "非本人报名"));

    mockMvc.perform(post("/course/learning/sessions")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content("""
                                 {"enrollmentId":1000,"courseItemId":1,"clientTraceId":"trace-1",
                                 "deviceType":"PC","deviceId":"device-web-001","startedTime":"2026-07-22T10:00:00",
                                 "validStudySeconds":60,"endReason":"NORMAL"}
                                 """))
      .andExpect(status().isForbidden())
      .andExpect(jsonPath("$.code").value(5075));
  }
}

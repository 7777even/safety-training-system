package com.sts.course.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sts.admin.entity.SysUser;
import com.sts.admin.mapper.SysUserMapper;
import com.sts.admin.tenant.TenantContext;
import com.sts.certificate.service.CertificateAutoIssueService;
import com.sts.common.api.PageRequest;
import com.sts.common.exception.BusinessException;
import com.sts.course.dto.CourseAssessmentResultEffective;
import com.sts.course.dto.CourseAssessmentSubmitted;
import com.sts.course.dto.CourseEvaluationSubmitRequest;
import com.sts.course.dto.StudySessionReportRequest;
import com.sts.course.entity.*;
import com.sts.course.mapper.*;
import com.sts.course.snapshot.CourseContentSnapshot;
import com.sts.course.vo.CourseLearningCourseVO;
import com.sts.course.vo.LearnerCourseDownloadAssetVO;
import com.sts.exam.entity.AssessmentExecution;
import com.sts.exam.entity.ExamAttempt;
import com.sts.exam.entity.ExamCandidate;
import com.sts.exam.mapper.ExamAttemptMapper;
import com.sts.exam.mapper.ExamCandidateMapper;
import com.sts.exam.service.AssessmentExecutionService;
import com.sts.training.event.TrainingParticipantContentFactChangedEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CourseLearningServiceImplTest {

  private static final Long ORG_ID = 1L;
  private static final Long OFFERING_ID = 100L;
  private static final Long COURSE_ID = 300L;
  private static final Long VERSION_ID = 200L;
  private static final Long ENROLLMENT_ID = 1000L;
  private static final Long USER_ID = 99L;
  private static final Long ITEM_REQUIRED = 101L;
  private static final Long ITEM_OPTIONAL = 102L;
  private static final Long SESSION_ID = 5000L;
  private static final String DEVICE_ID = "device-it-abc";

  private static final String SNAPSHOT_JSON = """
    {
      "schemaVersion": 1,
      "course": {"courseId": 10, "courseName": "消防安全教育", "sourceDownloadMode": "ALL"},
      "chapters": [
        {
          "chapterId": 1,
          "chapterName": "第一章",
          "items": [
            {
              "courseItemId": 101,
              "itemType": "ASSET",
              "isRequired": 1,
              "materialRule": { "requiredSeconds": 60 }
            },
            {
              "courseItemId": 102,
              "itemType": "ASSET",
              "isRequired": 0,
              "materialRule": { "requiredSeconds": 30 }
            }
          ]
        }
      ]
    }
    """;

  private CourseEnrollmentMapper enrollmentMapper;
  private CourseOfferingMapper offeringMapper;
  private CourseVersionMapper courseVersionMapper;
  private CourseItemProgressMapper itemProgressMapper;
  private CourseStudySessionMapper studySessionMapper;
  private CourseEvaluationMapper evaluationMapper;
  private SysUserMapper userMapper;
  private CourseSnapshotAssembler snapshotAssembler;
  private LearnerCourseResourceAssembler resourceAssembler;
  private CourseLearningServiceImpl service;
  private AssessmentExecutionService assessmentExecutionService;
  private CourseAssessmentResultMapper assessmentResultMapper;
  private ExamCandidateMapper examCandidateMapper;
  private ExamAttemptMapper examAttemptMapper;
  private ApplicationEventPublisher eventPublisher;
  private CertificateAutoIssueService certificateAutoIssueService;

  @BeforeEach
  void setUp() {
    TenantContext.setTenantId(ORG_ID_FOR_TENANT);
    enrollmentMapper = mock(CourseEnrollmentMapper.class);
    offeringMapper = mock(CourseOfferingMapper.class);
    courseVersionMapper = mock(CourseVersionMapper.class);
    itemProgressMapper = mock(CourseItemProgressMapper.class);
    studySessionMapper = mock(CourseStudySessionMapper.class);
    evaluationMapper = mock(CourseEvaluationMapper.class);
    userMapper = mock(SysUserMapper.class);
    snapshotAssembler = new CourseSnapshotAssembler(
      mock(com.sts.course.mapper.CourseCourseCategoryMapper.class),
      mock(com.sts.course.mapper.CourseChapterMapper.class),
      mock(com.sts.course.mapper.CourseItemMapper.class),
      mock(com.sts.course.mapper.CourseDownloadAssetMapper.class),
      mock(com.sts.asset.mapper.AssetMapper.class),
      mock(com.sts.course.service.CourseItemService.class),
      mock(com.sts.asset.service.AssetReferenceService.class),
      mock(com.sts.course.mapper.CourseItemQuestionBankMapper.class),
      mock(com.sts.question.mapper.QuestionBankMapper.class),
      mock(com.sts.paper.mapper.PaperVersionMapper.class),
      mock(com.sts.paper.mapper.PaperMapper.class), org.mockito.Mockito.mock(com.sts.course.mapper.TeacherFollowMapper.class));

    resourceAssembler = mock(LearnerCourseResourceAssembler.class);
    assessmentExecutionService = mock(AssessmentExecutionService.class);
    assessmentResultMapper = mock(CourseAssessmentResultMapper.class);
    examCandidateMapper = mock(ExamCandidateMapper.class);
    examAttemptMapper = mock(ExamAttemptMapper.class);
    eventPublisher = mock(ApplicationEventPublisher.class);
    certificateAutoIssueService = mock(CertificateAutoIssueService.class);
    service = new CourseLearningServiceImpl(
      enrollmentMapper, offeringMapper, courseVersionMapper, itemProgressMapper,
      studySessionMapper, evaluationMapper, userMapper, snapshotAssembler,
      mock(CourseOfferingEligibleUserMapper.class), resourceAssembler, assessmentExecutionService,
      assessmentResultMapper, examCandidateMapper, examAttemptMapper, eventPublisher,
      certificateAutoIssueService, org.mockito.Mockito.mock(com.sts.course.service.CourseCategoryService.class));
  }

  @AfterEach
  void clearTenantContext() {
    TenantContext.clear();
  }

  private static final Long ORG_ID_FOR_TENANT = 1L;

  @Test
  @DisplayName("reportSession：首次上报创建进度并累加秒数")
  void reportSession_firstSession_createsProgress() {
    stubLearningContext("NOT_STARTED", "ALL_REQUIRED_ITEMS");
    when(studySessionMapper.selectOne(any())).thenReturn(null);
    when(itemProgressMapper.selectOne(any())).thenReturn(null);
    doAnswer(inv -> {
      CourseStudySession s = inv.getArgument(0);
      s.setStudySessionId(SESSION_ID);
      return 1;
    }).when(studySessionMapper).insert(any(CourseStudySession.class));
    when(itemProgressMapper.selectList(any())).thenReturn(List.of());

    try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
      stp.when(StpUtil::getLoginIdAsLong).thenReturn(USER_ID);
      Long sessionId = service.reportSession(sessionRequest("trace-1", 60));
      assertThat(sessionId).isEqualTo(SESSION_ID);
    }

    ArgumentCaptor<CourseItemProgress> captor = ArgumentCaptor.forClass(CourseItemProgress.class);
    verify(itemProgressMapper).insert(captor.capture());
    CourseItemProgress progress = captor.getValue();
    assertThat(progress.getCourseItemId()).isEqualTo(ITEM_REQUIRED);
    assertThat(progress.getStudySeconds()).isEqualTo(60);
    assertThat(progress.getProgressStatus()).isEqualTo("COMPLETED");
    verify(enrollmentMapper).updateById(any(CourseEnrollment.class));
  }

  @Test
  @DisplayName("reportSession：培训报名状态变化时刷新投影")
  void reportSession_trainingEnrollment_statusChange_refreshesProjection() {
    stubLearningContext("NOT_STARTED", "ALL_REQUIRED_ITEMS", 2L, 11L);
    when(studySessionMapper.selectOne(any())).thenReturn(null);
    when(itemProgressMapper.selectOne(any())).thenReturn(null);
    doAnswer(inv -> {
      CourseStudySession s = inv.getArgument(0);
      s.setStudySessionId(SESSION_ID);
      return 1;
    }).when(studySessionMapper).insert(any(CourseStudySession.class));
    when(itemProgressMapper.selectList(any())).thenReturn(List.of());

    try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
      stp.when(StpUtil::getLoginIdAsLong).thenReturn(USER_ID);
      service.reportSession(sessionRequest("trace-train", 60));
    }

    assertTrainingProjectionEvent(2L, 11L);
  }

  @Test
  @DisplayName("reportSession：培训报名仅秒数变化不刷新投影")
  void reportSession_trainingEnrollment_sameStatus_doesNotRefresh() {
    stubLearningContext("LEARNING", "ALL_REQUIRED_ITEMS", 2L, 11L);
    CourseStudySession existing = new CourseStudySession();
    existing.setStudySessionId(SESSION_ID);
    existing.setClientTraceId("trace-seconds");
    when(studySessionMapper.selectOne(any())).thenReturn(null);
    CourseItemProgress progress = new CourseItemProgress();
    progress.setCourseItemId(ITEM_REQUIRED);
    progress.setProgressStatus("LEARNING");
    progress.setStudySeconds(10);
    progress.setAttemptCount(0);
    when(itemProgressMapper.selectOne(any())).thenReturn(progress);
    when(itemProgressMapper.selectList(any())).thenReturn(List.of(progress));
    doAnswer(inv -> {
      CourseStudySession s = inv.getArgument(0);
      s.setStudySessionId(SESSION_ID);
      return 1;
    }).when(studySessionMapper).insert(any(CourseStudySession.class));

    try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
      stp.when(StpUtil::getLoginIdAsLong).thenReturn(USER_ID);
      service.reportSession(sessionRequest("trace-seconds", 10));
    }

    verify(eventPublisher, never()).publishEvent(any(Object.class));
  }

  @Test
  @DisplayName("reportSession：独立开课不刷新培训投影")
  void reportSession_independentEnrollment_doesNotRefresh() {
    stubLearningContext("NOT_STARTED", "ALL_REQUIRED_ITEMS");
    when(studySessionMapper.selectOne(any())).thenReturn(null);
    when(itemProgressMapper.selectOne(any())).thenReturn(null);
    doAnswer(inv -> {
      CourseStudySession s = inv.getArgument(0);
      s.setStudySessionId(SESSION_ID);
      return 1;
    }).when(studySessionMapper).insert(any(CourseStudySession.class));
    when(itemProgressMapper.selectList(any())).thenReturn(List.of());

    try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
      stp.when(StpUtil::getLoginIdAsLong).thenReturn(USER_ID);
      service.reportSession(sessionRequest("trace-1", 60));
    }

    verify(eventPublisher, never()).publishEvent(any(Object.class));
  }

  @Test
  @DisplayName("reportSession：相同 clientTraceId 幂等不双计")
  void reportSession_duplicateTrace_noDoubleCount() {
    stubLearningContext("LEARNING", "ALL_REQUIRED_ITEMS");
    CourseStudySession existing = new CourseStudySession();
    existing.setStudySessionId(SESSION_ID);
    when(studySessionMapper.selectOne(any())).thenReturn(existing);

    try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
      stp.when(StpUtil::getLoginIdAsLong).thenReturn(USER_ID);
      Long sessionId = service.reportSession(sessionRequest("trace-dup", 60));
      assertThat(sessionId).isEqualTo(SESSION_ID);
    }

    verify(studySessionMapper, never()).insert(any(CourseStudySession.class));
    verify(itemProgressMapper, never()).insert(any(CourseItemProgress.class));
    verify(itemProgressMapper, never()).updateById(any(CourseItemProgress.class));
    verify(enrollmentMapper, never()).updateById(any(CourseEnrollment.class));
    verify(studySessionMapper, never()).selectOtherActiveContext(any(), any(), any(), any(), any());
    verify(studySessionMapper, never()).selectOtherUserActiveOnDevice(any(), any(), any());
  }

  @Test
  @DisplayName("reportSession：同一账户其他活跃学习上下文存在时拒绝并返回 5095")
  void reportSession_otherActiveContext_rejectedWithMutexCode() {
    stubLearningContext("LEARNING", "ALL_REQUIRED_ITEMS");
    when(studySessionMapper.selectOne(any())).thenReturn(null);
    CourseStudySession otherContext = new CourseStudySession();
    otherContext.setStudySessionId(6001L);
    otherContext.setCourseEnrollmentId(ENROLLMENT_ID);
    otherContext.setCourseItemId(ITEM_OPTIONAL);
    otherContext.setUserId(USER_ID);
    otherContext.setDeviceType("PC");
    when(studySessionMapper.selectOtherActiveContext(any(), any(), any(), any(), any())).thenReturn(otherContext);

    try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
      stp.when(StpUtil::getLoginIdAsLong).thenReturn(USER_ID);
      assertThatThrownBy(() -> service.reportSession(sessionRequest("trace-mutex", 30)))
        .isInstanceOf(BusinessException.class)
        .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(5095));
    }

    verify(studySessionMapper, never()).insert(any(CourseStudySession.class));
    verify(itemProgressMapper, never()).insert(any(CourseItemProgress.class));
    verify(itemProgressMapper, never()).updateById(any(CourseItemProgress.class));
    verify(enrollmentMapper, never()).updateById(any(CourseEnrollment.class));
  }

  @Test
  @DisplayName("reportSession：终端已绑定其他账户活跃学习时拒绝并返回 5096")
  void reportSession_deviceBoundToOtherUser_rejectedWithDeviceCode() {
    stubLearningContext("LEARNING", "ALL_REQUIRED_ITEMS");
    when(studySessionMapper.selectOne(any())).thenReturn(null);
    when(studySessionMapper.selectOtherActiveContext(any(), any(), any(), any(), any())).thenReturn(null);
    CourseStudySession otherUserSession = new CourseStudySession();
    otherUserSession.setStudySessionId(6002L);
    otherUserSession.setUserId(USER_ID + 1);
    otherUserSession.setDeviceType("H5");
    when(studySessionMapper.selectOtherUserActiveOnDevice(any(), any(), any())).thenReturn(otherUserSession);

    try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
      stp.when(StpUtil::getLoginIdAsLong).thenReturn(USER_ID);
      assertThatThrownBy(() -> service.reportSession(sessionRequest("trace-device", 30)))
        .isInstanceOf(BusinessException.class)
        .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(5096));
    }

    verify(studySessionMapper, never()).insert(any(CourseStudySession.class));
  }

  @Test
  @DisplayName("reportSession：首次上报把终端标识写入会话行")
  void reportSession_firstReport_persistsDeviceId() {
    stubLearningContext("NOT_STARTED", "ALL_REQUIRED_ITEMS");
    stubFirstReport();

    try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
      stp.when(StpUtil::getLoginIdAsLong).thenReturn(USER_ID);
      service.reportSession(sessionRequest("trace-device-1", 60));
    }

    ArgumentCaptor<CourseStudySession> captor = ArgumentCaptor.forClass(CourseStudySession.class);
    verify(studySessionMapper).insert(captor.capture());
    assertThat(captor.getValue().getDeviceId()).isEqualTo(DEVICE_ID);
  }

  @Test
  @DisplayName("reportSession：内容项最低学习秒数生效，不被材料规则默认值顶掉")
  void reportSession_itemMinimumSeconds_takesEffectOverDefaultRule() {
    // 材料规则只是机构默认值（15 秒），内容项显式要求 90 秒 ⇒ 阈值取 90，上报 60 秒不应完成。
    stubLearningContext("NOT_STARTED", "ALL_REQUIRED_ITEMS", null, null,
      thresholdSnapshotJson(90, "DEFAULT", 15));
    stubFirstReport();

    try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
      stp.when(StpUtil::getLoginIdAsLong).thenReturn(USER_ID);
      service.reportSession(sessionRequest("trace-item-min", 60));
    }

    ArgumentCaptor<CourseItemProgress> captor = ArgumentCaptor.forClass(CourseItemProgress.class);
    verify(itemProgressMapper).insert(captor.capture());
    assertThat(captor.getValue().getStudySeconds()).isEqualTo(60);
    assertThat(captor.getValue().getProgressStatus()).isEqualTo("LEARNING");
  }

  @Test
  @DisplayName("reportSession：自定义材料规则要求秒数覆盖内容项最低学习秒数")
  void reportSession_customRule_overridesItemMinimumSeconds() {
    // 内容项写 200 秒、自定义材料规则写 120 秒 ⇒ 以材料规则为准（覆盖语义，不取较大值）。
    stubLearningContext("NOT_STARTED", "ALL_REQUIRED_ITEMS", null, null,
      thresholdSnapshotJson(200, "CUSTOM", 120));
    stubFirstReport();

    try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
      stp.when(StpUtil::getLoginIdAsLong).thenReturn(USER_ID);
      service.reportSession(sessionRequest("trace-custom-rule", 150));
    }

    ArgumentCaptor<CourseItemProgress> captor = ArgumentCaptor.forClass(CourseItemProgress.class);
    verify(itemProgressMapper).insert(captor.capture());
    assertThat(captor.getValue().getProgressStatus()).isEqualTo("COMPLETED");
  }

  @Test
  @DisplayName("reportSession：完成阈值全为 0 时不判定完成")
  void reportSession_missingThreshold_doesNotComplete() {
    // 内容项门槛与材料规则要求秒数都为 0，表示未配置学习时长要求，不得据此直接判定完成。
    stubLearningContext("NOT_STARTED", "ALL_REQUIRED_ITEMS", null, null,
      thresholdSnapshotJson(0, "DEFAULT", 0));
    stubFirstReport();

    try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
      stp.when(StpUtil::getLoginIdAsLong).thenReturn(USER_ID);
      service.reportSession(sessionRequest("trace-no-threshold", 600));
    }

    ArgumentCaptor<CourseItemProgress> captor = ArgumentCaptor.forClass(CourseItemProgress.class);
    verify(itemProgressMapper).insert(captor.capture());
    assertThat(captor.getValue().getProgressStatus()).isEqualTo("LEARNING");
  }

  @Test
  @DisplayName("reportSession：测验内容项不会被普通学习会话直接标记完成")
  void reportSession_quizItem_doesNotCompleteFromStudySeconds() {
    stubLearningContext("LEARNING", "ALL_REQUIRED_ITEMS");
    CourseVersion examVersion = new CourseVersion();
    examVersion.setCourseVersionId(VERSION_ID);
    examVersion.setContentSnapshotJson(SNAPSHOT_JSON.replace("\"itemType\": \"ASSET\"",
                                                             "\"itemType\": \"QUIZ\", \"paperVersionId\": 1"));
    when(courseVersionMapper.selectById(VERSION_ID)).thenReturn(examVersion);
    when(studySessionMapper.selectOne(any())).thenReturn(null);
    when(itemProgressMapper.selectOne(any())).thenReturn(null);
    doAnswer(inv -> {
      CourseStudySession session = inv.getArgument(0);
      session.setStudySessionId(SESSION_ID);
      return 1;
    }).when(studySessionMapper).insert(any(CourseStudySession.class));
    when(itemProgressMapper.selectList(any())).thenReturn(List.of());

    try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
      stp.when(StpUtil::getLoginIdAsLong).thenReturn(USER_ID);
      service.reportSession(sessionRequest("trace-exam", 60));
    }

    ArgumentCaptor<CourseItemProgress> captor = ArgumentCaptor.forClass(CourseItemProgress.class);
    verify(itemProgressMapper).insert(captor.capture());
    assertThat(captor.getValue().getProgressStatus()).isEqualTo("LEARNING");
  }

  @Test
  @DisplayName("reportSession：ALL_REQUIRED_ITEMS 完成全部必修项后报名 COMPLETED")
  void reportSession_allRequiredItems_completesEnrollment() {
    stubLearningContext("LEARNING", "ALL_REQUIRED_ITEMS");
    when(studySessionMapper.selectOne(any())).thenReturn(null);
    when(itemProgressMapper.selectOne(any())).thenReturn(null);
    doAnswer(inv -> {
      CourseStudySession s = inv.getArgument(0);
      s.setStudySessionId(SESSION_ID);
      return 1;
    }).when(studySessionMapper).insert(any(CourseStudySession.class));

    CourseItemProgress completedRequired = new CourseItemProgress();
    completedRequired.setCourseItemId(ITEM_REQUIRED);
    completedRequired.setProgressStatus("COMPLETED");
    completedRequired.setStudySeconds(60);
    when(itemProgressMapper.selectList(any())).thenReturn(List.of(completedRequired));

    try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
      stp.when(StpUtil::getLoginIdAsLong).thenReturn(USER_ID);
      service.reportSession(sessionRequest("trace-complete", 60));
    }

    ArgumentCaptor<CourseEnrollment> captor = ArgumentCaptor.forClass(CourseEnrollment.class);
    verify(enrollmentMapper).updateById(captor.capture());
    assertThat(captor.getValue().getLearningStatus()).isEqualTo("COMPLETED");
    assertThat(captor.getValue().getCompletedTime()).isNotNull();
  }

  @Test
  @DisplayName("reportSession：独立开课完成时以 course_id 触发课程证书")
  void reportSession_independentCourseCompleted_issuesCourseCertificate() {
    stubLearningContext("LEARNING", "ALL_REQUIRED_ITEMS");
    stubCompletionReport();

    try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
      stp.when(StpUtil::getLoginIdAsLong).thenReturn(USER_ID);
      service.reportSession(sessionRequest("trace-course-cert", 60));
    }

    verify(certificateAutoIssueService).issueForSource("COURSE", COURSE_ID, USER_ID, ENROLLMENT_ID);
  }

  @Test
  @DisplayName("reportSession：培训开课完成时不触发独立课程证书")
  void reportSession_trainingCourseCompleted_skipsCourseCertificate() {
    stubLearningContext("LEARNING", "ALL_REQUIRED_ITEMS", 2L, 11L);
    stubCompletionReport();

    try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
      stp.when(StpUtil::getLoginIdAsLong).thenReturn(USER_ID);
      service.reportSession(sessionRequest("trace-training-course", 60));
    }

    verify(certificateAutoIssueService, never()).issueForSource(any(), any(), any(), any());
  }

  @Test
  @DisplayName("getMyEnrollmentTasks：未开始的课程测验返回 NOT_STARTED 与 UNPUBLISHED")
  void getMyEnrollmentTasks_quizWithoutExecution_exposesInitialState() {
    stubLearningContext("LEARNING", "ALL_REQUIRED_ITEMS");
    CourseVersion examVersion = new CourseVersion();
    examVersion.setCourseVersionId(VERSION_ID);
    examVersion.setContentSnapshotJson(SNAPSHOT_JSON
                                         .replace("\"courseName\": \"消防安全教育\"",
                                                  "\"courseName\": \"考试课程\"")
                                         .replace("\"itemType\": \"ASSET\"",
                                                  "\"itemType\": \"QUIZ\", \"paperVersionId\": 1"));
    when(courseVersionMapper.selectById(VERSION_ID)).thenReturn(examVersion);
    when(itemProgressMapper.selectList(any())).thenReturn(List.of());

    try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
      stp.when(StpUtil::getLoginIdAsLong).thenReturn(USER_ID);
      var tasks = service.getMyEnrollmentTasks(ENROLLMENT_ID);
      var item = tasks.getChapters().getFirst().getItems().getFirst();
      assertThat(item.getExamTaskStatus()).isEqualTo("NOT_STARTED");
      assertThat(item.getScorePublishStatus()).isEqualTo("UNPUBLISHED");
      assertThat(item.getFinalScore()).isNull();
    }
  }

  @Test
  @DisplayName("getMyEnrollmentTasks：旧考试 Candidate 不得回写课程测验状态")
  void getMyEnrollmentTasks_legacyCandidate_doesNotAffectQuiz() {
    stubLearningContext("LEARNING", "ALL_REQUIRED_ITEMS");
    CourseVersion examVersion = new CourseVersion();
    examVersion.setCourseVersionId(VERSION_ID);
    examVersion.setContentSnapshotJson(SNAPSHOT_JSON
                                         .replace("\"courseName\": \"消防安全教育\"",
                                                  "\"courseName\": \"考试课程\"")
                                         .replace("\"itemType\": \"ASSET\"",
                                                  "\"itemType\": \"QUIZ\", \"paperVersionId\": 1"));
    when(courseVersionMapper.selectById(VERSION_ID)).thenReturn(examVersion);
    when(itemProgressMapper.selectList(any())).thenReturn(List.of(), List.of());
    try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
      stp.when(StpUtil::getLoginIdAsLong).thenReturn(USER_ID);
      var tasks = service.getMyEnrollmentTasks(ENROLLMENT_ID);
      var item = tasks.getChapters().getFirst().getItems().getFirst();
      assertThat(item.getExamTaskStatus()).isEqualTo("NOT_STARTED");
      assertThat(item.getProgressStatus()).isEqualTo("NOT_STARTED");
      assertThat(item.getCompletedTime()).isNull();
    }

    verify(itemProgressMapper, never()).insert(any(CourseItemProgress.class));
    verify(enrollmentMapper, never()).updateById(any(CourseEnrollment.class));
  }

  @Test
  @DisplayName("getMyEnrollmentTasks：课程测验仅从所属执行返回已生效成绩")
  void getMyEnrollmentTasks_courseAssessmentResultEffective_exposesFinalState() {
    stubLearningContext("COMPLETED", "ALL_REQUIRED_ITEMS");
    CourseVersion examVersion = new CourseVersion();
    examVersion.setCourseVersionId(VERSION_ID);
    examVersion.setContentSnapshotJson(SNAPSHOT_JSON
                                         .replace("\"courseName\": \"消防安全教育\"", "\"courseName\": \"考试课程\"")
                                         .replace("\"itemType\": \"ASSET\"",
                                                  "\"itemType\": \"QUIZ\", \"paperVersionId\": 1"));
    when(courseVersionMapper.selectById(VERSION_ID)).thenReturn(examVersion);
    when(itemProgressMapper.selectList(any())).thenReturn(List.of());
    ExamCandidate candidate = new ExamCandidate();
    candidate.setExamCandidateId(301L);
    candidate.setCourseEnrollmentId(ENROLLMENT_ID);
    candidate.setCourseItemId(ITEM_REQUIRED);
    candidate.setFinalAttemptId(401L);
    candidate.setFinalScore(new BigDecimal("88.00"));
    candidate.setCandidateStatus("PASSED");
    candidate.setResultEffectiveTime(LocalDateTime.now());
    when(examCandidateMapper.selectList(any())).thenReturn(List.of(candidate));
    ExamAttempt attempt = new ExamAttempt();
    attempt.setExamAttemptId(401L);
    attempt.setExamCandidateId(301L);
    attempt.setAttemptStatus("RESULT_EFFECTIVE");
    attempt.setFinalScore(new BigDecimal("88.00"));
    attempt.setIsPassed(1);
    attempt.setResultEffectiveTime(LocalDateTime.now());
    when(examAttemptMapper.selectList(any())).thenReturn(List.of(attempt));

    try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
      stp.when(StpUtil::getLoginIdAsLong).thenReturn(USER_ID);
      var item = service.getMyEnrollmentTasks(ENROLLMENT_ID).getChapters().getFirst().getItems().getFirst();
      assertThat(item.getExamTaskStatus()).isEqualTo("RESULT_EFFECTIVE");
      assertThat(item.getScorePublishStatus()).isEqualTo("EFFECTIVE");
      assertThat(item.getPublishedExamAttemptId()).isEqualTo(401L);
      assertThat(item.getFinalScore()).isEqualByComparingTo("88.00");
      assertThat(item.getIsPassed()).isEqualTo(1);
    }
  }

  @Test
  @DisplayName("getMyEnrollmentTasks：待人工批阅课程测验只能等待结果，不得重新开始")
  void getMyEnrollmentTasks_pendingReviewQuiz_exposesWaitingResultWithSubmittedAttempt() {
    stubLearningContext("LEARNING", "ALL_REQUIRED_ITEMS");
    CourseVersion examVersion = new CourseVersion();
    examVersion.setCourseVersionId(VERSION_ID);
    examVersion.setContentSnapshotJson(SNAPSHOT_JSON
      .replace("\"itemType\": \"ASSET\"", "\"itemType\": \"QUIZ\", \"paperVersionId\": 1"));
    when(courseVersionMapper.selectById(VERSION_ID)).thenReturn(examVersion);
    when(itemProgressMapper.selectList(any())).thenReturn(List.of());
    ExamCandidate candidate = new ExamCandidate();
    candidate.setExamCandidateId(301L);
    candidate.setCourseEnrollmentId(ENROLLMENT_ID);
    candidate.setCourseItemId(ITEM_REQUIRED);
    when(examCandidateMapper.selectList(any())).thenReturn(List.of(candidate));
    ExamAttempt attempt = new ExamAttempt();
    attempt.setExamAttemptId(401L);
    attempt.setExamCandidateId(301L);
    attempt.setAttemptStatus("PENDING_REVIEW");
    when(examAttemptMapper.selectList(any())).thenReturn(List.of(attempt));

    try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
      stp.when(StpUtil::getLoginIdAsLong).thenReturn(USER_ID);
      var item = service.getMyEnrollmentTasks(ENROLLMENT_ID).getChapters().getFirst().getItems().getFirst();
      assertThat(item.getExamTaskStatus()).isEqualTo("WAITING_RESULT");
      assertThat(item.getActiveExamAttemptId()).isEqualTo(401L);
      assertThat(item.getPublishedExamAttemptId()).isNull();
    }
  }

  @Test
  @DisplayName("submitEvaluation：未完成学习不可评价 → 400 / 5082")
  void submitEvaluation_notCompleted_throws5082() {
    CourseEnrollment enrollment = enrollment(ENROLLMENT_ID, USER_ID, "LEARNING");
    when(enrollmentMapper.selectOne(any())).thenReturn(enrollment);

    CourseEvaluationSubmitRequest request = new CourseEvaluationSubmitRequest();
    request.setRating(BigDecimal.valueOf(4.5));
    request.setIsAnonymous(0);

    try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
      stp.when(StpUtil::getLoginIdAsLong).thenReturn(USER_ID);
      assertThatThrownBy(() -> service.submitEvaluation(ENROLLMENT_ID, request))
        .isInstanceOf(BusinessException.class)
        .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(5082));
    }
    verify(evaluationMapper, never()).insert(any(CourseEvaluation.class));
  }

  @Test
  @DisplayName("submitEvaluation：重复提交 → 409 / 5085")
  void submitEvaluation_duplicate_throws5085() {
    CourseEnrollment enrollment = enrollment(ENROLLMENT_ID, USER_ID, "COMPLETED");
    when(enrollmentMapper.selectOne(any())).thenReturn(enrollment);
    when(evaluationMapper.selectOne(any())).thenReturn(new CourseEvaluation());

    CourseEvaluationSubmitRequest request = new CourseEvaluationSubmitRequest();
    request.setRating(BigDecimal.valueOf(5.0));
    request.setIsAnonymous(1);

    try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
      stp.when(StpUtil::getLoginIdAsLong).thenReturn(USER_ID);
      assertThatThrownBy(() -> service.submitEvaluation(ENROLLMENT_ID, request))
        .isInstanceOf(BusinessException.class)
        .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(5085));
    }
  }

  @Test
  @DisplayName("同次开课评价分页：实名展示姓名，匿名不查询身份")
  void pagePeerEvaluations_masksAnonymousAndScopesOffering() {
    CourseEnrollment enrollment = new CourseEnrollment();
    enrollment.setCourseEnrollmentId(ENROLLMENT_ID);
    enrollment.setCourseOfferingId(OFFERING_ID);
    enrollment.setUserId(USER_ID);
    enrollment.setLearningStatus("LEARNING");
    when(enrollmentMapper.selectOne(any())).thenReturn(enrollment);
    CourseEvaluation named = new CourseEvaluation();
    named.setUserId(101L);
    named.setRating(BigDecimal.valueOf(4.5));
    named.setIsAnonymous(0);
    named.setSubmittedTime(LocalDateTime.of(2026, 9, 29, 10, 30));
    CourseEvaluation anonymous = new CourseEvaluation();
    anonymous.setUserId(102L);
    anonymous.setRating(BigDecimal.valueOf(5));
    anonymous.setIsAnonymous(1);
    anonymous.setSubmittedTime(LocalDateTime.of(2026, 9, 29, 11, 45));
    Page<CourseEvaluation> records = new Page<>(1, 20);
    records.setRecords(List.of(named, anonymous));
    records.setTotal(2);
    when(evaluationMapper.selectPage(any(), any())).thenReturn(records);
    SysUser user = new SysUser();
    user.setUserId(101L);
    user.setUserName("张同学");
    when(userMapper.selectList(any())).thenReturn(List.of(user));

    try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
      stp.when(StpUtil::getLoginIdAsLong).thenReturn(USER_ID);
      var result = service.pagePeerEvaluations(ENROLLMENT_ID, PageRequest.of(1, 20, null));
      assertThat(result.total()).isEqualTo(2);
      assertThat(result.list()).extracting("displayName").containsExactly("张同学", "匿名学员");
      assertThat(result.list()).extracting("rating").containsExactly(BigDecimal.valueOf(4.5), BigDecimal.valueOf(5));
      assertThat(result.list()).extracting("submittedTime").containsExactly("2026-09-29T10:30", "2026-09-29");
      verify(userMapper).selectList(any());
    }
  }

  @Test
  @DisplayName("同次开课评价分页：他人报名和已退选报名均拒绝")
  void pagePeerEvaluations_rejectsForeignAndWithdrawnEnrollment() {
    CourseEnrollment enrollment = new CourseEnrollment();
    enrollment.setCourseOfferingId(OFFERING_ID);
    enrollment.setUserId(101L);
    enrollment.setLearningStatus("LEARNING");
    when(enrollmentMapper.selectOne(any())).thenReturn(enrollment);
    try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
      stp.when(StpUtil::getLoginIdAsLong).thenReturn(USER_ID);
      assertThatThrownBy(() -> service.pagePeerEvaluations(ENROLLMENT_ID, PageRequest.of(1, 20, null)))
        .isInstanceOf(BusinessException.class)
        .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(5081));
      enrollment.setUserId(USER_ID);
      enrollment.setLearningStatus("WITHDRAWN");
      assertThatThrownBy(() -> service.pagePeerEvaluations(ENROLLMENT_ID, PageRequest.of(1, 20, null)))
        .isInstanceOf(BusinessException.class);
      verify(evaluationMapper, never()).selectPage(any(), any());
    }
  }

  @Test
  @DisplayName("getMyEnrollmentDetail：聚合冻结课程简介与附件")
  void getMyEnrollmentDetail_aggregatesFrozenCourseAndAssets() {
    stubLearningContext("COMPLETED", "ALL_REQUIRED_ITEMS");
    when(itemProgressMapper.selectList(any())).thenReturn(List.of());
    CourseLearningCourseVO course = new CourseLearningCourseVO();
    course.setCourseId(10L);
    course.setCourseName("消防安全教育");
    LearnerCourseDownloadAssetVO asset = new LearnerCourseDownloadAssetVO();
    asset.setAssetId(30L);
    asset.setDownloadable(true);
    when(resourceAssembler.toCourse(any())).thenReturn(course);
    when(resourceAssembler.toDownloadAssets(eq(ENROLLMENT_ID), any(CourseContentSnapshot.class)))
      .thenReturn(List.of(asset));

    try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
      stp.when(StpUtil::getLoginIdAsLong).thenReturn(USER_ID);
      var detail = service.getMyEnrollmentDetail(ENROLLMENT_ID);
      assertThat(detail.getCourse().getCourseName()).isEqualTo("消防安全教育");
      assertThat(detail.getDownloadAssets()).extracting(LearnerCourseDownloadAssetVO::getAssetId)
        .containsExactly(30L);
    }
  }

  @Test
  @DisplayName("applyEffectiveAssessmentResult：课程题库完成一轮后只完成对应报名内容项")
  void applyEffectiveAssessmentResult_courseBankCompletesOnlyOwnedItem() {
    stubLearningContext("LEARNING", "ALL_REQUIRED_ITEMS");
    CourseVersion version = new CourseVersion();
    version.setCourseVersionId(VERSION_ID);
    version.setContentSnapshotJson(SNAPSHOT_JSON.replace(
      "\"itemType\": \"ASSET\"", "\"itemType\": \"QUESTION_BANK\", \"questionBankId\": 9"));
    when(courseVersionMapper.selectById(VERSION_ID)).thenReturn(version);
    AssessmentExecution execution = new AssessmentExecution();
    execution.setAssessmentExecutionId(300L);
    execution.setSourceType("COURSE_BANK_PRACTICE");
    when(assessmentExecutionService.requireCourse(OFFERING_ID, VERSION_ID, ITEM_REQUIRED))
      .thenReturn(execution);
    when(itemProgressMapper.selectOne(any())).thenReturn(null);
    CourseItemProgress completed = new CourseItemProgress();
    completed.setCourseItemId(ITEM_REQUIRED);
    completed.setProgressStatus("COMPLETED");
    completed.setStudySeconds(0);
    when(itemProgressMapper.selectList(any())).thenReturn(List.of(completed));

    service.applyEffectiveAssessmentResult(new CourseAssessmentResultEffective(
      ORG_ID, 300L, 400L, 500L, USER_ID, ENROLLMENT_ID, OFFERING_ID,
      ITEM_REQUIRED, "COURSE_BANK_PRACTICE", 1, LocalDateTime.now(),
      new BigDecimal("8"), 1, 8, 2));

    verify(assessmentResultMapper).insert(argThat((CourseAssessmentResult result) ->
                                                    result.getExamAttemptId().equals(400L) && result.getAssessmentExecutionId().equals(
                                                      300L)));
    verify(itemProgressMapper).insert(argThat((CourseItemProgress progress) ->
                                                progress.getCourseEnrollmentId().equals(ENROLLMENT_ID)
                                                  && progress.getCourseItemId().equals(ITEM_REQUIRED)
                                                  && "COMPLETED".equals(progress.getProgressStatus())
                                                  && progress.getBestScore().compareTo(new BigDecimal("8")) == 0));
    verify(enrollmentMapper).updateById(argThat((CourseEnrollment enrollment) ->
                                                  "COMPLETED".equals(enrollment.getLearningStatus())));
  }

  @Test
  @DisplayName("applySubmittedAssessmentResult：课程测验交卷即投影待批阅进度且不写成绩事实")
  void applySubmittedAssessmentResult_courseQuiz_projectsPendingProgress() {
    stubLearningContext("LEARNING", "ALL_REQUIRED_ITEMS");
    stubQuizSnapshot();
    LocalDateTime submittedTime = LocalDateTime.of(2026, 9, 17, 14, 35);
    when(itemProgressMapper.selectOne(any())).thenReturn(null);
    CourseItemProgress projected = new CourseItemProgress();
    projected.setCourseItemId(ITEM_REQUIRED);
    projected.setProgressStatus("LEARNING");
    projected.setStudySeconds(0);
    when(itemProgressMapper.selectList(any())).thenReturn(List.of(projected));

    service.applySubmittedAssessmentResult(new CourseAssessmentSubmitted(
      ORG_ID, 300L, 400L, 500L, USER_ID, ENROLLMENT_ID, OFFERING_ID,
      ITEM_REQUIRED, "COURSE_QUIZ", 1, submittedTime));

    ArgumentCaptor<CourseItemProgress> captor = ArgumentCaptor.forClass(CourseItemProgress.class);
    verify(itemProgressMapper).insert(captor.capture());
    CourseItemProgress progress = captor.getValue();
    assertThat(progress.getCourseEnrollmentId()).isEqualTo(ENROLLMENT_ID);
    assertThat(progress.getCourseItemId()).isEqualTo(ITEM_REQUIRED);
    assertThat(progress.getProgressStatus()).isEqualTo("LEARNING");
    assertThat(progress.getAttemptCount()).isEqualTo(1);
    assertThat(progress.getStudySeconds()).isZero();
    assertThat(progress.getFirstStartTime()).isEqualTo(submittedTime);
    assertThat(progress.getLastStudyTime()).isEqualTo(submittedTime);
    // 交卷只是参与事实：不得写入生效结果，否则成绩生效回调会被幂等短路。
    verify(assessmentResultMapper, never()).insert(any(CourseAssessmentResult.class));
    // 待批阅期间内容项不得计入已完成，进度百分比保持 0。
    verify(enrollmentMapper).updateById(argThat((CourseEnrollment enrollment) ->
                                                  enrollment.getProgressPercent().compareTo(BigDecimal.ZERO) == 0
                                                    && "LEARNING".equals(enrollment.getLearningStatus())));
  }

  @Test
  @DisplayName("applySubmittedAssessmentResult：交卷后再次交卷只刷新时间且不重复计作答次数")
  void applySubmittedAssessmentResult_repeatedCallback_keepsAttemptCount() {
    stubLearningContext("LEARNING", "ALL_REQUIRED_ITEMS");
    stubQuizSnapshot();
    CourseItemProgress existing = new CourseItemProgress();
    existing.setCourseItemId(ITEM_REQUIRED);
    existing.setProgressStatus("LEARNING");
    existing.setAttemptCount(1);
    existing.setStudySeconds(0);
    LocalDateTime submittedTime = LocalDateTime.of(2026, 9, 17, 15, 0);
    when(itemProgressMapper.selectOne(any())).thenReturn(existing);
    when(itemProgressMapper.selectList(any())).thenReturn(List.of(existing));

    service.applySubmittedAssessmentResult(new CourseAssessmentSubmitted(
      ORG_ID, 300L, 400L, 500L, USER_ID, ENROLLMENT_ID, OFFERING_ID,
      ITEM_REQUIRED, "COURSE_QUIZ", 1, submittedTime));

    verify(itemProgressMapper, never()).insert(any(CourseItemProgress.class));
    verify(itemProgressMapper).updateById(argThat((CourseItemProgress progress) ->
                                                   progress.getAttemptCount() == 1
                                                     && submittedTime.equals(progress.getLastStudyTime())
                                                     && "LEARNING".equals(progress.getProgressStatus())));
  }

  @Test
  @DisplayName("applyEffectiveAssessmentResult：交卷已投影时只转已完成且作答次数保持 1")
  void applyEffectiveAssessmentResult_afterSubmittedProjection_keepsAttemptCount() {
    stubLearningContext("LEARNING", "ALL_REQUIRED_ITEMS");
    stubQuizSnapshot();
    CourseItemProgress existing = new CourseItemProgress();
    existing.setCourseItemId(ITEM_REQUIRED);
    existing.setProgressStatus("LEARNING");
    existing.setAttemptCount(1);
    existing.setStudySeconds(0);
    LocalDateTime submittedTime = LocalDateTime.of(2026, 9, 17, 14, 35);
    existing.setFirstStartTime(submittedTime);
    existing.setLastStudyTime(submittedTime);
    when(itemProgressMapper.selectOne(any())).thenReturn(existing);
    when(itemProgressMapper.selectList(any())).thenReturn(List.of(existing));

    service.applyEffectiveAssessmentResult(new CourseAssessmentResultEffective(
      ORG_ID, 300L, 400L, 500L, USER_ID, ENROLLMENT_ID, OFFERING_ID,
      ITEM_REQUIRED, "COURSE_QUIZ", 1, LocalDateTime.of(2026, 9, 17, 16, 0),
      new BigDecimal("88"), 1, 8, 2));

    verify(itemProgressMapper, never()).insert(any(CourseItemProgress.class));
    verify(itemProgressMapper).updateById(argThat((CourseItemProgress progress) ->
                                                   progress.getAttemptCount() == 1
                                                     && "COMPLETED".equals(progress.getProgressStatus())
                                                     && progress.getBestScore().compareTo(new BigDecimal("88")) == 0));
  }

  @Test
  @DisplayName("补测进行中以新作答状态为准，不继续显示首测失败")
  void finalAssessment_retakeInProgressOverridesFirstFailure() {
    stubLearningContext("LEARNING", "ALL_REQUIRED_ITEMS"); stubQuizSnapshot();
    var version = courseVersionMapper.selectById(VERSION_ID);
    version.setContentSnapshotJson(version.getContentSnapshotJson()
      .replace("\"chapterId\": 1", "\"chapterId\": 1, \"status\": 1")
      .replace("\"courseItemId\": 101", "\"courseItemId\": 101, \"status\": 1, \"isFinalAssessment\": 1"));
    var candidate = new ExamCandidate(); candidate.setExamCandidateId(400L); candidate.setCourseItemId(ITEM_REQUIRED); candidate.setAttemptCount(2);
    when(examCandidateMapper.selectList(any())).thenReturn(List.of(candidate));
    var first = new ExamAttempt(); first.setExamCandidateId(400L); first.setExamAttemptId(500L);
    first.setAttemptNo(1); first.setAttemptStatus("RESULT_EFFECTIVE"); first.setIsPassed(0);
    var second = new ExamAttempt(); second.setExamCandidateId(400L); second.setExamAttemptId(501L);
    second.setAttemptNo(2); second.setAttemptStatus("ANSWERING");
    when(examAttemptMapper.selectList(any())).thenReturn(List.of(second, first));
    try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
      stp.when(StpUtil::getLoginIdAsLong).thenReturn(USER_ID);
      var task = service.getMyEnrollmentTasks(ENROLLMENT_ID).getChapters().getFirst().getItems().getFirst();
      assertThat(task.getExamTaskStatus()).isEqualTo("IN_PROGRESS");
      assertThat(task.getActiveExamAttemptId()).isEqualTo(501L);
      assertThat(task.getCanRetake()).isFalse();
    }
  }

  @Test
  @DisplayName("结业开考必须先完成其他必需内容，不能伪造报名用户")
  void finalAssessment_readinessAndOwnerAreEnforced() {
    stubLearningContext("LEARNING", "ALL_ITEMS");
    stubQuizSnapshot();
    var version = courseVersionMapper.selectById(VERSION_ID);
    version.setContentSnapshotJson(version.getContentSnapshotJson()
      .replace("\"chapterId\": 1", "\"chapterId\": 1, \"status\": 1")
      .replace("\"courseItemId\": 101", "\"courseItemId\": 101, \"status\": 1, \"isFinalAssessment\": 1"));
    try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
      stp.when(StpUtil::getLoginIdAsLong).thenReturn(USER_ID);
      assertThatThrownBy(() -> service.assertFinalAssessmentReady(ENROLLMENT_ID, ITEM_REQUIRED, USER_ID))
        .isInstanceOf(BusinessException.class).satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(5087));
      var material = new CourseItemProgress();
      material.setCourseItemId(ITEM_OPTIONAL);
      material.setProgressStatus("COMPLETED");
      when(itemProgressMapper.selectList(any())).thenReturn(List.of(material));
      service.assertFinalAssessmentReady(ENROLLMENT_ID, ITEM_REQUIRED, USER_ID);
      assertThatThrownBy(() -> service.assertFinalAssessmentReady(ENROLLMENT_ID, ITEM_REQUIRED, USER_ID + 1))
        .isInstanceOf(BusinessException.class);
    }
  }

  @Test
  @DisplayName("结业测评失败不完成课程或发证")
  void finalAssessment_failedKeepsLearning() {
    stubLearningContext("LEARNING", "ALL_REQUIRED_ITEMS");
    stubQuizSnapshot();
    CourseVersion version = courseVersionMapper.selectById(VERSION_ID);
    version.setContentSnapshotJson(version.getContentSnapshotJson().replace("\"courseItemId\": 101", "\"courseItemId\": 101, \"isFinalAssessment\": 1, \"status\": 1"));
    CourseItemProgress progress = new CourseItemProgress();
    progress.setCourseItemId(ITEM_REQUIRED);
    progress.setProgressStatus("LEARNING");
    progress.setAttemptCount(1);
    progress.setStudySeconds(120);
    when(itemProgressMapper.selectOne(any())).thenReturn(progress);
    when(itemProgressMapper.selectList(any())).thenReturn(List.of(progress));
    service.applyEffectiveAssessmentResult(new CourseAssessmentResultEffective(
      ORG_ID, 300L, 400L, 500L, USER_ID, ENROLLMENT_ID, OFFERING_ID,
      ITEM_REQUIRED, "COURSE_QUIZ", 1, LocalDateTime.now(), new BigDecimal("40"), 0, 4, 6));
    assertThat(progress.getProgressStatus()).isEqualTo("LEARNING");
    verify(certificateAutoIssueService, never()).issueForSource(any(), any(), any(), any());
  }

  @Test
  @DisplayName("结业合格认可当时有效学习时长")
  void finalAssessment_passRecognizesStudySeconds() {
    stubLearningContext("LEARNING", "ALL_REQUIRED_ITEMS");
    stubQuizSnapshot();
    CourseVersion version = courseVersionMapper.selectById(VERSION_ID);
    version.setContentSnapshotJson(version.getContentSnapshotJson().replace("\"courseItemId\": 101", "\"courseItemId\": 101, \"isFinalAssessment\": 1, \"status\": 1"));
    CourseItemProgress progress = new CourseItemProgress();
    progress.setCourseItemId(ITEM_REQUIRED);
    progress.setProgressStatus("LEARNING");
    progress.setAttemptCount(1);
    progress.setStudySeconds(120);
    when(itemProgressMapper.selectOne(any())).thenReturn(progress);
    when(itemProgressMapper.selectList(any())).thenReturn(List.of(progress));
    service.applyEffectiveAssessmentResult(new CourseAssessmentResultEffective(
      ORG_ID, 300L, 400L, 500L, USER_ID, ENROLLMENT_ID, OFFERING_ID,
      ITEM_REQUIRED, "COURSE_QUIZ", 1, LocalDateTime.now(), new BigDecimal("80"), 1, 8, 2));
    assertThat(progress.getProgressStatus()).isEqualTo("COMPLETED");
    verify(enrollmentMapper).updateById(argThat((CourseEnrollment row) ->
      Integer.valueOf(120).equals(row.getRecognizedStudySeconds()) && "COMPLETED".equals(row.getLearningStatus())));
  }

  @Test
  @DisplayName("课程测验生效：培训课程完成时自动写入课程正式成绩")
  void applyEffectiveAssessmentResult_completedTrainingCourse_writesFinalScore() {
    stubLearningContext("LEARNING", "ALL_REQUIRED_ITEMS", 88L, 91L);
    stubQuizSnapshot();
    CourseItemProgress completed = new CourseItemProgress();
    completed.setCourseItemId(ITEM_REQUIRED);
    completed.setProgressStatus("COMPLETED");
    completed.setAttemptCount(1);
    completed.setStudySeconds(0);
    when(itemProgressMapper.selectOne(any())).thenReturn(completed);
    when(itemProgressMapper.selectList(any())).thenReturn(List.of(completed));
    when(examCandidateMapper.selectList(any())).thenReturn(List.of(candidate(ITEM_REQUIRED, "85")));

    service.applyEffectiveAssessmentResult(new CourseAssessmentResultEffective(
      ORG_ID, 300L, 400L, 500L, USER_ID, ENROLLMENT_ID, OFFERING_ID,
      ITEM_REQUIRED, "COURSE_QUIZ", 1, LocalDateTime.of(2026, 9, 18, 16, 0),
      new BigDecimal("85"), 1, 8, 2));

    verify(enrollmentMapper).updateById(argThat((CourseEnrollment enrollment) ->
      "COMPLETED".equals(enrollment.getLearningStatus())
        && new BigDecimal("85.00").compareTo(enrollment.getFinalScore()) == 0));
  }

  /** 把冻结快照的首个内容项替换为课程测验，并冻结对应考核执行对象。 */
  @Test
  void liveParticipation_completesWithoutExternalSeconds() {
    String json = SNAPSHOT_JSON.replace("\"chapterId\": 1", "\"chapterId\": 1, \"status\": 1").replace("\"courseItemId\": 101", "\"courseItemId\": 101, \"status\": 1").replace("\"itemType\": \"ASSET\"", "\"itemType\": \"LIVE\"");
    stubLearningContext("LEARNING", "ALL_REQUIRED_ITEMS", null, null, json);
    com.sts.course.service.CourseItemService items = (com.sts.course.service.CourseItemService)
      org.springframework.test.util.ReflectionTestUtils.getField(snapshotAssembler, "itemService");
    com.sts.course.vo.LiveSessionVO live = availableLive();
    when(items.getLiveSession(ITEM_REQUIRED)).thenReturn(live);
    try (MockedStatic<StpUtil> login = mockStatic(StpUtil.class)) {
      login.when(StpUtil::getLoginIdAsLong).thenReturn(USER_ID);
      assertThat(service.participateLive(ENROLLMENT_ID, ITEM_REQUIRED).getProgressStatus()).isEqualTo("COMPLETED");
      ArgumentCaptor<CourseItemProgress> progress = ArgumentCaptor.forClass(CourseItemProgress.class);
      verify(itemProgressMapper).insert(progress.capture());
      assertThat(progress.getValue().getStudySeconds()).isZero();
    }
  }

  @Test
  void liveParticipation_cancelledRejected() {
    String json = SNAPSHOT_JSON.replace("\"chapterId\": 1", "\"chapterId\": 1, \"status\": 1").replace("\"courseItemId\": 101", "\"courseItemId\": 101, \"status\": 1").replace("\"itemType\": \"ASSET\"", "\"itemType\": \"LIVE\"");
    stubLearningContext("LEARNING", "ALL_REQUIRED_ITEMS", null, null, json);
    com.sts.course.service.CourseItemService items = (com.sts.course.service.CourseItemService)
      org.springframework.test.util.ReflectionTestUtils.getField(snapshotAssembler, "itemService");
    com.sts.course.vo.LiveSessionVO live = availableLive();
    live.setLiveStatus("CANCELLED");
    when(items.getLiveSession(ITEM_REQUIRED)).thenReturn(live);
    try (MockedStatic<StpUtil> login = mockStatic(StpUtil.class)) {
      login.when(StpUtil::getLoginIdAsLong).thenReturn(USER_ID);
      assertThatThrownBy(() -> service.participateLive(ENROLLMENT_ID, ITEM_REQUIRED))
        .isInstanceOf(BusinessException.class);
      verify(itemProgressMapper, never()).insert(any(CourseItemProgress.class));
    }
  }

  private com.sts.course.vo.LiveSessionVO availableLive() {
    var live = new com.sts.course.vo.LiveSessionVO();
    live.setLiveUrl("https://example.com/live");
    live.setStatus(1);
    live.setLiveStatus("SCHEDULED");
    live.setStartTime(LocalDateTime.now().minusHours(1));
    live.setEndTime(LocalDateTime.now().plusHours(1));
    return live;
  }

  @Test
  void report_usesLatestEffectiveResultAndCorrectDenominator() {
    stubLearningContext("LEARNING", "ALL_REQUIRED_ITEMS");
    CourseAssessmentResult old = new CourseAssessmentResult();
    old.setCourseItemId(101L);
    old.setAttemptNo(1);
    old.setCorrectCount(1);
    old.setWrongCount(9);
    CourseAssessmentResult latest = new CourseAssessmentResult();
    latest.setCourseItemId(101L);
    latest.setAttemptNo(2);
    latest.setCorrectCount(4);
    latest.setWrongCount(1);
    when(assessmentResultMapper.selectList(any())).thenReturn(List.of(old, latest));
    try (MockedStatic<StpUtil> login = mockStatic(StpUtil.class)) {
      login.when(StpUtil::getLoginIdAsLong).thenReturn(USER_ID);
      var results = service.getMyEnrollmentDetail(ENROLLMENT_ID).getAssessmentResults();
      assertThat(results).hasSize(1);
      assertThat(results.get(0).getAccuracyPercent()).isEqualByComparingTo("80");
    }
  }

  private void stubQuizSnapshot() {
    CourseVersion version = new CourseVersion();
    version.setCourseVersionId(VERSION_ID);
    version.setContentSnapshotJson(SNAPSHOT_JSON.replace(
      "\"itemType\": \"ASSET\"", "\"itemType\": \"QUIZ\", \"paperVersionId\": 9"));
    when(courseVersionMapper.selectById(VERSION_ID)).thenReturn(version);
    AssessmentExecution execution = new AssessmentExecution();
    execution.setAssessmentExecutionId(300L);
    execution.setSourceType("COURSE_QUIZ");
    when(assessmentExecutionService.requireCourse(OFFERING_ID, VERSION_ID, ITEM_REQUIRED))
      .thenReturn(execution);
  }

  private void assertTrainingProjectionEvent(Long trainingParticipantId, Long planContentId) {
    ArgumentCaptor<ApplicationEvent> captor = ArgumentCaptor.forClass(ApplicationEvent.class);
    verify(eventPublisher).publishEvent(captor.capture());
    assertThat(captor.getValue()).isInstanceOfSatisfying(TrainingParticipantContentFactChangedEvent.class, fact -> {
      assertThat(fact.getTrainingParticipantId()).isEqualTo(trainingParticipantId);
      assertThat(fact.getPlanContentId()).isEqualTo(planContentId);
    });
  }

  private void stubLearningContext(String learningStatus, String completionRule) {
    stubLearningContext(learningStatus, completionRule, null, null);
  }

  private void stubLearningContext(String learningStatus, String completionRule,
                                   Long trainingParticipantId, Long planContentId) {
    stubLearningContext(learningStatus, completionRule, trainingParticipantId, planContentId, SNAPSHOT_JSON);
  }

  private void stubLearningContext(String learningStatus, String completionRule,
                                   Long trainingParticipantId, Long planContentId, String snapshotJson) {
    CourseEnrollment enrollment = enrollment(ENROLLMENT_ID, USER_ID, learningStatus);
    enrollment.setTrainingParticipantId(trainingParticipantId);
    when(enrollmentMapper.selectOne(any())).thenReturn(enrollment);

    CourseOffering offering = new CourseOffering();
    offering.setCourseOfferingId(OFFERING_ID);
    offering.setOrganizationId(ORG_ID);
    offering.setCourseVersionId(VERSION_ID);
    offering.setPublishStatus("PUBLISHED");
    offering.setCompletionRule(completionRule);
    offering.setCourseId(COURSE_ID);
    offering.setPlanContentId(planContentId);
    when(offeringMapper.selectOne(any())).thenReturn(offering);

    CourseVersion version = new CourseVersion();
    version.setCourseVersionId(VERSION_ID);
    version.setContentSnapshotJson(snapshotJson);
    when(courseVersionMapper.selectById(VERSION_ID)).thenReturn(version);
  }

  /** 首次上报的通用桩：无既有会话、无既有进度记录。 */
  private void stubFirstReport() {
    when(studySessionMapper.selectOne(any())).thenReturn(null);
    when(itemProgressMapper.selectOne(any())).thenReturn(null);
    doAnswer(inv -> {
      CourseStudySession s = inv.getArgument(0);
      s.setStudySessionId(SESSION_ID);
      return 1;
    }).when(studySessionMapper).insert(any(CourseStudySession.class));
    when(itemProgressMapper.selectList(any())).thenReturn(List.of());
  }

  /** 首次上报即完成的桩：无既有会话与进度，必选项上报后已完成。 */
  private void stubCompletionReport() {
    stubFirstReport();
    CourseItemProgress completedRequired = new CourseItemProgress();
    completedRequired.setCourseItemId(ITEM_REQUIRED);
    completedRequired.setProgressStatus("COMPLETED");
    completedRequired.setStudySeconds(60);
    when(itemProgressMapper.selectList(any())).thenReturn(List.of(completedRequired));
  }

  /** 构造只含一个必修为 ASSET 内容项的快照，用于验证完成阈值解析顺序。 */
  private static String thresholdSnapshotJson(Integer itemMinimumSeconds, String ruleSource,
                                              Integer ruleRequiredSeconds) {
    return """
      {
        "schemaVersion": 1,
        "course": {"courseId": 10, "courseName": "消防安全教育", "sourceDownloadMode": "ALL"},
        "chapters": [
          {
            "chapterId": 1,
            "chapterName": "第一章",
            "items": [
              {
                "courseItemId": 101,
                "itemType": "ASSET",
                "isRequired": 1,
                "minimumStudySeconds": %s,
                "materialRule": {"source": %s, "requiredSeconds": %s}
              }
            ]
          }
        ]
      }
      """.formatted(
      itemMinimumSeconds == null ? "null" : String.valueOf(itemMinimumSeconds),
      ruleSource == null ? "null" : "\"" + ruleSource + "\"",
      ruleRequiredSeconds == null ? "null" : String.valueOf(ruleRequiredSeconds));
  }

  /** 课程考生的 finalScore 已按既有重考策略收敛，是课程平均分的唯一分项来源。 */
  private static ExamCandidate candidate(Long courseItemId, String finalScore) {
    ExamCandidate candidate = new ExamCandidate();
    candidate.setCourseEnrollmentId(ENROLLMENT_ID);
    candidate.setCourseItemId(courseItemId);
    candidate.setFinalScore(new BigDecimal(finalScore));
    return candidate;
  }

  private static StudySessionReportRequest sessionRequest(String traceId, int seconds) {
    StudySessionReportRequest request = new StudySessionReportRequest();
    request.setEnrollmentId(ENROLLMENT_ID);
    request.setCourseItemId(ITEM_REQUIRED);
    request.setClientTraceId(traceId);
    request.setDeviceType("PC");
    request.setDeviceId(DEVICE_ID);
    request.setStartedTime(LocalDateTime.now().minusMinutes(1));
    request.setEndedTime(LocalDateTime.now());
    request.setValidStudySeconds(seconds);
    request.setEndReason("COMPLETED");
    return request;
  }

  private static CourseEnrollment enrollment(Long id, Long userId, String learningStatus) {
    CourseEnrollment enrollment = new CourseEnrollment();
    enrollment.setCourseEnrollmentId(id);
    enrollment.setOrganizationId(ORG_ID);
    enrollment.setCourseOfferingId(OFFERING_ID);
    enrollment.setUserId(userId);
    enrollment.setLearningStatus(learningStatus);
    enrollment.setProgressPercent(BigDecimal.ZERO);
    enrollment.setTotalStudySeconds(0);
    enrollment.setCreditAwarded(BigDecimal.ZERO);
    return enrollment;
  }
}

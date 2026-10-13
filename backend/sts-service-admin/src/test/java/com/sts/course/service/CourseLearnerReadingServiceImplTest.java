package com.sts.course.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sts.admin.tenant.TenantContext;
import com.sts.common.api.PageRequest;
import com.sts.common.api.PageResult;
import com.sts.course.dto.LearnerCourseEnrollmentQuery;
import com.sts.course.dto.LearnerCourseOfferingQuery;
import com.sts.course.entity.CourseEnrollment;
import com.sts.course.entity.CourseItemProgress;
import com.sts.course.entity.CourseOffering;
import com.sts.course.entity.CourseVersion;
import com.sts.course.mapper.*;
import com.sts.course.vo.LearnerCourseEnrollmentVO;
import com.sts.course.vo.LearnerCourseOfferingVO;
import com.sts.course.vo.LearnerCourseTasksVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CourseLearnerReadingServiceImplTest {

  private static final Long ORG_ID = 1L;
  private static final Long USER_ID = 99L;
  private static final Long ORG_ID_FOR_TENANT = 1L;

  @BeforeAll
  static void initMybatisTableInfo() {
    MybatisConfiguration configuration = new MybatisConfiguration();
    MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
    TableInfoHelper.initTableInfo(assistant, CourseEnrollment.class);
  }

  private CourseEnrollmentMapper enrollmentMapper;
  private CourseOfferingMapper offeringMapper;
  private CourseVersionMapper versionMapper;
  private CourseItemProgressMapper itemProgressMapper;
  private CourseOfferingEligibleUserMapper eligibleUserMapper;
  private CourseLearningServiceImpl service;
  private CourseCategoryService categoryService;

  @BeforeEach
  void setUp() {
    TenantContext.setTenantId(ORG_ID_FOR_TENANT);
    enrollmentMapper = mock(CourseEnrollmentMapper.class);
    offeringMapper = mock(CourseOfferingMapper.class);
    versionMapper = mock(CourseVersionMapper.class);
    itemProgressMapper = mock(CourseItemProgressMapper.class);
    eligibleUserMapper = mock(CourseOfferingEligibleUserMapper.class);

    categoryService = mock(CourseCategoryService.class);
    service = new CourseLearningServiceImpl(
      enrollmentMapper, offeringMapper, versionMapper,
      itemProgressMapper, mock(CourseStudySessionMapper.class),
      mock(CourseEvaluationMapper.class), mock(com.sts.admin.mapper.SysUserMapper.class), snapshotAssembler(),
      eligibleUserMapper, mock(LearnerCourseResourceAssembler.class),
      mock(com.sts.exam.service.AssessmentExecutionService.class),
      mock(CourseAssessmentResultMapper.class),
      mock(com.sts.exam.mapper.ExamCandidateMapper.class),
      mock(com.sts.exam.mapper.ExamAttemptMapper.class),
      mock(org.springframework.context.ApplicationEventPublisher.class),
      mock(com.sts.certificate.service.CertificateAutoIssueService.class), categoryService);
  }

  @AfterEach
  void clearTenantContext() {
    TenantContext.clear();
  }

  @Test
  @DisplayName("我的课程强制当前登录人并从冻结快照返回展示字段")
  void pageMyEnrollments_scopesCurrentUserAndUsesSnapshot() {
    CourseEnrollment enrollment = new CourseEnrollment();
    enrollment.setCourseEnrollmentId(1000L);
    enrollment.setCourseOfferingId(100L);
    enrollment.setUserId(USER_ID);
    enrollment.setLearningStatus("LEARNING");
    enrollment.setProgressPercent(new BigDecimal("35.00"));
    when(enrollmentMapper.selectPage(any(Page.class), any())).thenReturn(new Page<CourseEnrollment>(1, 20, 1)
                                                                           .setRecords(List.of(enrollment)));

    when(offeringMapper.selectBatchIds(any())).thenReturn(List.of(offering(100L, 10L, 200L, null)));
    when(versionMapper.selectBatchIds(any())).thenReturn(List.of(version(200L, "安全生产基础", 900L, List.of(7L))));

    LearnerCourseEnrollmentQuery query = new LearnerCourseEnrollmentQuery();
    query.setLearningStatus("LEARNING");
    try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
      stp.when(StpUtil::getLoginIdAsLong).thenReturn(USER_ID);
      PageResult<LearnerCourseEnrollmentVO> result =
        service.pageMyEnrollments(PageRequest.of(1, 20, query));

      assertThat(result.total()).isEqualTo(1);
      assertThat(result.list()).singleElement().satisfies(vo -> {
        assertThat(vo.getEnrollmentId()).isEqualTo(1000L);
        assertThat(vo.getCourseName()).isEqualTo("安全生产基础");
        assertThat(vo.getCoverUrl()).isEqualTo("/api/course/courses/10/cover");
      });
    }
    @SuppressWarnings("unchecked")
    ArgumentCaptor<LambdaQueryWrapper<CourseEnrollment>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
    verify(enrollmentMapper).selectPage(any(Page.class), captor.capture());
    assertThat(captor.getValue().getSqlSegment()).containsIgnoringCase("enrollment_source");
    assertThat(captor.getValue().getParamNameValuePairs()).containsValue("TRAINING");
  }

  @Test
  @DisplayName("培训来源报名仍可按报名 ID 读取学习任务")
  void getMyEnrollmentTasks_allowsTrainingSourceForSelf() {
    CourseEnrollment enrollment = new CourseEnrollment();
    enrollment.setCourseEnrollmentId(1000L);
    enrollment.setCourseOfferingId(100L);
    enrollment.setUserId(USER_ID);
    enrollment.setEnrollmentSource("TRAINING");
    enrollment.setLearningStatus("LEARNING");
    enrollment.setProgressPercent(new BigDecimal("50.00"));
    when(enrollmentMapper.selectOne(any())).thenReturn(enrollment);
    when(offeringMapper.selectOne(any())).thenReturn(offering(100L, 10L, 200L, null));

    CourseVersion version = new CourseVersion();
    version.setCourseVersionId(200L);
    version.setContentSnapshotJson("""
                                     {"schemaVersion":1,
                                      "course":{"courseId":10,"courseName":"安全生产基础","coverFileId":900},
                                      "chapters":[{"chapterId":1,"chapterName":"第一章","sortOrder":1,"status":1,
                                        "items":[
                                          {"courseItemId":101,"itemType":"ASSET","itemTitle":"事故案例","assetId":501,
                                           "isRequired":1,"sortOrder":1,"status":1}
                                        ]}]}
                                     """);
    when(versionMapper.selectById(200L)).thenReturn(version);
    when(itemProgressMapper.selectList(any())).thenReturn(List.of());

    try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
      stp.when(StpUtil::getLoginIdAsLong).thenReturn(USER_ID);
      LearnerCourseTasksVO result = service.getMyEnrollmentTasks(1000L);
      assertThat(result.getCourseName()).isEqualTo("安全生产基础");
      assertThat(result.getChapters()).isNotEmpty();
    }
  }

  @Test
  @DisplayName("父分类包含后代并保留开班白名单过滤")
  void pageLearnerOfferings_filtersEligibility() {
    LocalDateTime now = LocalDateTime.now();
    CourseOffering eligible = offering(100L, 10L, 200L, 300L);
    eligible.setStartTime(now.minusDays(1));
    eligible.setEndTime(now.plusDays(1));
    CourseOffering outsideWhitelist = offering(101L, 11L, 201L, 301L);
    outsideWhitelist.setStartTime(now.minusDays(1));
    outsideWhitelist.setEndTime(now.plusDays(1));
    when(offeringMapper.selectList(any())).thenReturn(List.of(eligible, outsideWhitelist));
    when(enrollmentMapper.selectList(any())).thenReturn(List.of());
    when(versionMapper.selectBatchIds(any())).thenReturn(List.of(
      version(200L, "安全生产基础", 900L, List.of(7L)),
      version(201L, "化工安全", 901L, List.of(7L))));
    when(eligibleUserMapper.selectCount(any())).thenReturn(1L, 0L);

    LearnerCourseOfferingQuery query = new LearnerCourseOfferingQuery();
    query.setKeyword("安全");
    query.setCategoryId(6L);
    var parent = new com.sts.course.vo.CourseCategoryTreeVO();
    parent.setCourseCategoryId(6L);
    var child = new com.sts.course.vo.CourseCategoryTreeVO();
    child.setCourseCategoryId(7L);
    child.setParentId(6L);
    parent.getChildren().add(child);
    when(categoryService.getCategoryTree(null, 1)).thenReturn(List.of(parent));
    query.setOfferingStatus("AVAILABLE");
    try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
      stp.when(StpUtil::getLoginIdAsLong).thenReturn(USER_ID);
      PageResult<LearnerCourseOfferingVO> result =
        service.pageLearnerOfferings(PageRequest.of(1, 20, query));

      assertThat(result.total()).isEqualTo(1);
      assertThat(result.list()).singleElement().satisfies(vo -> {
        assertThat(vo.getOfferingId()).isEqualTo(100L);
        assertThat(vo.getEnrollmentAvailability()).isEqualTo("AVAILABLE");
      });
    }
  }

  @Test
  @DisplayName("学习任务从冻结快照组装并为无进度内容项提供默认事实")
  void getMyEnrollmentTasks_mergesSnapshotAndProgressDefaults() {
    CourseEnrollment enrollment = new CourseEnrollment();
    enrollment.setCourseEnrollmentId(1000L);
    enrollment.setCourseOfferingId(100L);
    enrollment.setUserId(USER_ID);
    enrollment.setLearningStatus("LEARNING");
    enrollment.setProgressPercent(new BigDecimal("50.00"));
    when(enrollmentMapper.selectOne(any())).thenReturn(enrollment);
    when(offeringMapper.selectOne(any())).thenReturn(offering(100L, 10L, 200L, null));

    CourseVersion version = new CourseVersion();
    version.setCourseVersionId(200L);
    version.setContentSnapshotJson("""
                                     {"schemaVersion":1,
                                      "course":{"courseId":10,"courseName":"安全生产基础","coverFileId":900},
                                      "chapters":[{"chapterId":1,"chapterName":"第一章","sortOrder":1,"status":1,
                                        "items":[
                                          {"courseItemId":101,"itemType":"ASSET","itemTitle":"事故案例","assetId":501,
                                           "isRequired":1,"sortOrder":1,"status":1},
                                          {"courseItemId":102,"itemType":"LIVE","itemTitle":"在线讲解",
                                           "isRequired":0,"sortOrder":2,"status":0}
                                        ]}]}
                                     """);
    when(versionMapper.selectById(200L)).thenReturn(version);

    CourseItemProgress progress = new CourseItemProgress();
    progress.setCourseItemId(101L);
    progress.setProgressStatus("LEARNING");
    progress.setStudySeconds(30);
    when(itemProgressMapper.selectList(any())).thenReturn(List.of(progress));

    try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
      stp.when(StpUtil::getLoginIdAsLong).thenReturn(USER_ID);
      LearnerCourseTasksVO result = service.getMyEnrollmentTasks(1000L);

      assertThat(result.getCourseName()).isEqualTo("安全生产基础");
      assertThat(result.getCoverUrl()).isEqualTo("/api/course/courses/10/cover");
      assertThat(result.getChapters()).singleElement().satisfies(chapter -> {
        assertThat(chapter.getItems()).hasSize(2);
        assertThat(chapter.getItems().get(0).getProgressStatus()).isEqualTo("LEARNING");
        assertThat(chapter.getItems().get(0).getAssetId()).isEqualTo(501L);
        assertThat(chapter.getItems().get(1).getProgressStatus()).isEqualTo("NOT_STARTED");
        assertThat(chapter.getItems().get(1).getStudySeconds()).isZero();
        assertThat(chapter.getItems().get(1).getLearnable()).isFalse();
        assertThat(chapter.getItems().get(1).getUnavailableReason()).isEqualTo("CONTENT_DISABLED");
      });
    }
  }

  @Test
  @DisplayName("非报名本人读取学习任务返回 403")
  void getMyEnrollmentTasks_otherUserDenied() {
    CourseEnrollment enrollment = new CourseEnrollment();
    enrollment.setCourseEnrollmentId(1000L);
    enrollment.setUserId(USER_ID + 1);
    when(enrollmentMapper.selectOne(any())).thenReturn(enrollment);

    try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
      stp.when(StpUtil::getLoginIdAsLong).thenReturn(USER_ID);
      assertThatThrownBy(() -> service.getMyEnrollmentTasks(1000L))
        .isInstanceOf(com.sts.common.exception.BusinessException.class)
        .satisfies(error -> assertThat(((com.sts.common.exception.BusinessException) error).getCode())
          .isEqualTo(5081));
    }
    verify(offeringMapper, never()).selectOne(any());
  }

  @Test
  @DisplayName("过期报名的启用内容项仍不可学习")
  void getMyEnrollmentTasks_expiredEnrollmentIsUnavailable() {
    CourseEnrollment enrollment = new CourseEnrollment();
    enrollment.setCourseEnrollmentId(1000L);
    enrollment.setCourseOfferingId(100L);
    enrollment.setUserId(USER_ID);
    enrollment.setLearningStatus("EXPIRED");
    when(enrollmentMapper.selectOne(any())).thenReturn(enrollment);
    when(offeringMapper.selectOne(any())).thenReturn(offering(100L, 10L, 200L, null));
    when(versionMapper.selectById(200L)).thenReturn(version(200L, "安全生产基础", 900L, List.of()));
    CourseVersion version = versionMapper.selectById(200L);
    version.setContentSnapshotJson("""
                                     {"schemaVersion":1,"course":{"courseId":10,"courseName":"安全生产基础"},
                                      "chapters":[{"chapterId":1,"chapterName":"第一章","status":1,
                                        "items":[{"courseItemId":101,"itemType":"ASSET","itemTitle":"案例","status":1}]}]}
                                     """);
    when(itemProgressMapper.selectList(any())).thenReturn(List.of());

    try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
      stp.when(StpUtil::getLoginIdAsLong).thenReturn(USER_ID);
      assertThat(service.getMyEnrollmentTasks(1000L).getChapters().getFirst().getItems().getFirst())
        .satisfies(item -> {
          assertThat(item.getLearnable()).isFalse();
          assertThat(item.getUnavailableReason()).isEqualTo("ENROLLMENT_EXPIRED");
        });
    }
  }

  private CourseOffering offering(Long offeringId, Long courseId, Long versionId, Long whitelistMarker) {
    CourseOffering offering = new CourseOffering();
    offering.setCourseOfferingId(offeringId);
    offering.setOrganizationId(ORG_ID);
    offering.setCourseId(courseId);
    offering.setCourseVersionId(versionId);
    offering.setOfferingName("2026年度安全班");
    offering.setCourseType("REQUIRED");
    offering.setEnrollmentType("OPEN");
    offering.setEligibilityType(whitelistMarker == null ? "ALL" : "SELECTED");
    offering.setPublishStatus("PUBLISHED");
    offering.setStatus(1);
    return offering;
  }

  private CourseVersion version(Long versionId, String courseName, Long coverFileId, List<Long> categoryIds) {
    CourseVersion version = new CourseVersion();
    version.setCourseVersionId(versionId);
    version.setContentSnapshotJson("""
                                     {"schemaVersion":1,"course":{"courseId":10,"courseName":"%s","coverFileId":%d},"categoryIds":%s,"chapters":[]}
                                     """.formatted(courseName, coverFileId, categoryIds));
    return version;
  }

  private CourseSnapshotAssembler snapshotAssembler() {
    return new CourseSnapshotAssembler(
      mock(CourseCourseCategoryMapper.class), mock(CourseChapterMapper.class),
      mock(CourseItemMapper.class), mock(CourseDownloadAssetMapper.class), mock(com.sts.asset.mapper.AssetMapper.class),
      mock(CourseItemService.class), mock(com.sts.asset.service.AssetReferenceService.class),
      mock(CourseItemQuestionBankMapper.class), mock(com.sts.question.mapper.QuestionBankMapper.class),
      mock(com.sts.paper.mapper.PaperVersionMapper.class), mock(com.sts.paper.mapper.PaperMapper.class), org.mockito.Mockito.mock(com.sts.course.mapper.TeacherFollowMapper.class));
  }
}

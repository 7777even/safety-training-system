package com.sts.course.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sts.admin.tenant.TenantContext;
import com.sts.common.api.PageRequest;
import com.sts.common.exception.BusinessException;
import com.sts.course.dto.CourseOfferingCreateRequest;
import com.sts.course.dto.CourseOfferingListQuery;
import com.sts.course.dto.CourseOfferingPublishRequest;
import com.sts.course.entity.Course;
import com.sts.course.entity.CourseOffering;
import com.sts.course.entity.CourseVersion;
import com.sts.course.mapper.CourseMapper;
import com.sts.course.mapper.CourseOfferingEligibleUserMapper;
import com.sts.course.mapper.CourseOfferingMapper;
import com.sts.course.mapper.CourseVersionMapper;
import com.sts.course.snapshot.CourseContentSnapshot;
import com.sts.exam.service.AssessmentExecutionService;
import com.sts.exam.service.AssessmentReviewerService;
import com.sts.person.dto.PersonSelectionRequest;
import com.sts.person.service.PersonSelectionService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CourseOfferingServiceImplTest {

  private static final Long ORG_ID = 1L;
  private static final Long COURSE_ID = 10L;
  private static final Long OFFERING_ID = 100L;
  private static final Long VERSION_ID = 200L;

  private CourseOfferingMapper offeringMapper;
  private CourseMapper courseMapper;
  private CourseOfferingEligibleUserMapper eligibleUserMapper;
  private PersonSelectionService personSelectionService;
  private CourseVersionMapper courseVersionMapper;
  private CourseSnapshotAssembler snapshotAssembler;
  private CourseOfferingServiceImpl service;
  private AssessmentExecutionService assessmentExecutionService;
  private AssessmentReviewerService assessmentReviewerService;

  @BeforeAll
  static void initMybatisTableInfo() {
    MybatisConfiguration configuration = new MybatisConfiguration();
    MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
    TableInfoHelper.initTableInfo(assistant, CourseOffering.class);
  }

  @BeforeEach
  void setUp() {
    TenantContext.setTenantId(ORG_ID_FOR_TENANT);
    offeringMapper = mock(CourseOfferingMapper.class);
    courseMapper = mock(CourseMapper.class);
    eligibleUserMapper = mock(CourseOfferingEligibleUserMapper.class);
    personSelectionService = mock(PersonSelectionService.class);
    courseVersionMapper = mock(CourseVersionMapper.class);
    snapshotAssembler = mock(CourseSnapshotAssembler.class);
    assessmentExecutionService = mock(AssessmentExecutionService.class);
    assessmentReviewerService = mock(AssessmentReviewerService.class);
    service = new CourseOfferingServiceImpl(offeringMapper, courseMapper, eligibleUserMapper, personSelectionService,
                                            courseVersionMapper, snapshotAssembler,
                                            assessmentExecutionService, assessmentReviewerService);
  }

  @AfterEach
  void clearTenantContext() {
    TenantContext.clear();
  }

  private static final Long ORG_ID_FOR_TENANT = 1L;

  @Test
  @DisplayName("publishOffering：发布时冻结 course_version_id 并置 PUBLISHED")
  void publishOffering_freezesVersion() {
    CourseOffering offering = offering(OFFERING_ID, STATUS_DRAFT, null, 0);
    when(offeringMapper.selectOne(any())).thenReturn(offering);

    Course course = course(COURSE_ID, "PUBLISHED", VERSION_ID);
    when(courseMapper.selectOne(any())).thenReturn(course);
    CourseVersion version = new CourseVersion();
    version.setCourseVersionId(VERSION_ID);
    version.setContentSnapshotJson("{}");
    when(courseVersionMapper.selectById(VERSION_ID)).thenReturn(version);
    when(snapshotAssembler.parseContentSnapshot("{}")).thenReturn(new CourseContentSnapshot());
    com.sts.exam.entity.AssessmentExecution execution = new com.sts.exam.entity.AssessmentExecution();
    execution.setAssessmentExecutionId(300L);
    execution.setSourceType("COURSE_QUIZ");
    when(assessmentExecutionService.ensureCourseExecutions(any(), any())).thenReturn(java.util.List.of(execution));

    CourseOfferingPublishRequest request = new CourseOfferingPublishRequest();
    request.setVersion(0);

    service.publishOffering(OFFERING_ID, request);

    ArgumentCaptor<CourseOffering> captor = ArgumentCaptor.forClass(CourseOffering.class);
    verify(offeringMapper).updateByIdWithVersionCheck(captor.capture());
    CourseOffering updated = captor.getValue();
    assertThat(updated.getCourseVersionId()).isEqualTo(VERSION_ID);
    assertThat(updated.getPublishStatus()).isEqualTo("PUBLISHED");
    assertThat(updated.getPublishedTime()).isNotNull();
    verify(assessmentReviewerService).assertCourseReviewersConfigured(java.util.List.of(execution));
  }

  @Test
  @DisplayName("publishOffering：开课已有版本则使用已有版本，不取课程 current")
  void publishOffering_usesExistingVersionInsteadOfCurrent() {
    CourseOffering offering = offering(OFFERING_ID, STATUS_DRAFT, 50L, 0);
    when(offeringMapper.selectOne(any())).thenReturn(offering);
    when(courseMapper.selectOne(any())).thenReturn(course(COURSE_ID, "PUBLISHED", VERSION_ID));
    CourseVersion frozen = new CourseVersion();
    frozen.setCourseVersionId(50L);
    frozen.setCourseId(COURSE_ID);
    frozen.setContentSnapshotJson("{}");
    when(courseVersionMapper.selectById(50L)).thenReturn(frozen);
    when(snapshotAssembler.parseContentSnapshot("{}")).thenReturn(new CourseContentSnapshot());
    when(assessmentExecutionService.ensureCourseExecutions(any(), any())).thenReturn(java.util.List.of());

    CourseOfferingPublishRequest request = new CourseOfferingPublishRequest();
    request.setVersion(0);
    service.publishOffering(OFFERING_ID, request);

    ArgumentCaptor<CourseOffering> captor = ArgumentCaptor.forClass(CourseOffering.class);
    verify(offeringMapper).updateByIdWithVersionCheck(captor.capture());
    assertThat(captor.getValue().getCourseVersionId()).isEqualTo(50L);
    assertThat(captor.getValue().getPublishStatus()).isEqualTo("PUBLISHED");
    verify(courseVersionMapper, never()).selectById(VERSION_ID);
  }

  @Test
  @DisplayName("publishOffering：已发布开课只再校验阅卷人，不改写版本")
  void publishOffering_alreadyPublished_reassertsReviewers() {
    CourseOffering offering = offering(OFFERING_ID, "PUBLISHED", 50L, 1);
    when(offeringMapper.selectOne(any())).thenReturn(offering);
    when(courseMapper.selectOne(any())).thenReturn(course(COURSE_ID, "PUBLISHED", VERSION_ID));
    CourseVersion frozen = new CourseVersion();
    frozen.setCourseVersionId(50L);
    frozen.setCourseId(COURSE_ID);
    frozen.setContentSnapshotJson("{}");
    when(courseVersionMapper.selectById(50L)).thenReturn(frozen);
    when(snapshotAssembler.parseContentSnapshot("{}")).thenReturn(new CourseContentSnapshot());
    when(assessmentExecutionService.ensureCourseExecutions(any(), any())).thenReturn(java.util.List.of());

    CourseOfferingPublishRequest request = new CourseOfferingPublishRequest();
    request.setVersion(1);
    service.publishOffering(OFFERING_ID, request);

    verify(assessmentReviewerService).assertCourseReviewersConfigured(any());
    verify(offeringMapper, never()).updateByIdWithVersionCheck(any());
  }

  @Test
  @DisplayName("publishOffering：课程未发布或无版本 → 400 / 5062")
  void publishOffering_courseNotReady_throws5062() {
    CourseOffering offering = offering(OFFERING_ID, STATUS_DRAFT, null, 0);
    when(offeringMapper.selectOne(any())).thenReturn(offering);

    Course course = course(COURSE_ID, "DRAFT", null);
    when(courseMapper.selectOne(any())).thenReturn(course);

    CourseOfferingPublishRequest request = new CourseOfferingPublishRequest();
    request.setVersion(0);

    assertThatThrownBy(() -> service.publishOffering(OFFERING_ID, request))
      .isInstanceOf(BusinessException.class)
      .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(5062));
    verify(offeringMapper, never()).updateByIdWithVersionCheck(any());
  }

  @Test
  @DisplayName("publishOffering：缺阅卷人抛 5068 且不改开课状态")
  void publishOffering_missingReviewers_throws5068WithoutUpdate() {
    CourseOffering offering = offering(OFFERING_ID, STATUS_DRAFT, 50L, 0);
    when(offeringMapper.selectOne(any())).thenReturn(offering);
    when(courseMapper.selectOne(any())).thenReturn(course(COURSE_ID, "PUBLISHED", VERSION_ID));
    CourseVersion frozen = new CourseVersion();
    frozen.setCourseVersionId(50L);
    frozen.setCourseId(COURSE_ID);
    frozen.setContentSnapshotJson("{}");
    when(courseVersionMapper.selectById(50L)).thenReturn(frozen);
    when(snapshotAssembler.parseContentSnapshot("{}")).thenReturn(new CourseContentSnapshot());
    when(assessmentExecutionService.ensureCourseExecutions(any(), any())).thenReturn(java.util.List.of());
    doThrow(new BusinessException(HttpStatus.BAD_REQUEST, 5068, "含主观题的课程测验必须配置阅卷人"))
      .when(assessmentReviewerService).assertCourseReviewersConfigured(any());

    CourseOfferingPublishRequest request = new CourseOfferingPublishRequest();
    request.setVersion(0);

    assertThatThrownBy(() -> service.publishOffering(OFFERING_ID, request))
      .isInstanceOf(BusinessException.class)
      .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(5068));
    verify(offeringMapper, never()).updateByIdWithVersionCheck(any());
  }

  @Test
  @DisplayName("deleteOffering：非草稿状态 → 400 / 5064")
  void deleteOffering_nonDraft_throws5064() {
    CourseOffering offering = offering(OFFERING_ID, "PUBLISHED", VERSION_ID, 1);
    when(offeringMapper.selectOne(any())).thenReturn(offering);

    assertThatThrownBy(() -> service.deleteOffering(OFFERING_ID))
      .isInstanceOf(BusinessException.class)
      .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(5064));
  }

  @Test
  @DisplayName("createOffering：ASSIGNED 不再保存自助和外部报名开关")
  void createOffering_assignedEnrollment_usesSimpleModel() {
    Course course = course(COURSE_ID, "PUBLISHED", VERSION_ID);
    when(courseMapper.selectOne(any())).thenReturn(course);
    doAnswer(invocation -> {
      CourseOffering entity = invocation.getArgument(0);
      entity.setCourseOfferingId(OFFERING_ID);
      return 1;
    }).when(offeringMapper).insert(any(CourseOffering.class));

    CourseOfferingCreateRequest request = new CourseOfferingCreateRequest();
    request.setOfferingCode("OFF-001");
    request.setCourseId(COURSE_ID);
    request.setOfferingName("春季班");
    request.setCourseType("REQUIRED");
    request.setEnrollmentType("ASSIGNED");
    request.setEligibilityType("ALL");

    service.createOffering(request);

    ArgumentCaptor<CourseOffering> captor = ArgumentCaptor.forClass(CourseOffering.class);
    verify(offeringMapper).insert(captor.capture());
    CourseOffering inserted = captor.getValue();
    assertThat(inserted.getEligibilityType()).isEqualTo("ALL");
    assertThat(inserted.getCompletionRule()).isEqualTo("ALL_REQUIRED_ITEMS");
    assertThat(inserted.getPublishStatus()).isEqualTo("DRAFT");
  }

  @Test
  @DisplayName("createOffering：OPEN SELECTED 将选人结果展开为具体白名单")
  void createOffering_openSelected_persistsConcreteEligibleUsers() {
    when(courseMapper.selectOne(any())).thenReturn(course(COURSE_ID, "PUBLISHED", VERSION_ID));
    doAnswer(invocation -> {
      CourseOffering entity = invocation.getArgument(0);
      entity.setCourseOfferingId(OFFERING_ID);
      return 1;
    }).when(offeringMapper).insert(any(CourseOffering.class));
    when(personSelectionService.resolve(any())).thenReturn(java.util.List.of(8L, 9L));

    CourseOfferingCreateRequest request = new CourseOfferingCreateRequest();
    request.setOfferingCode("OFF-002");
    request.setCourseId(COURSE_ID);
    request.setOfferingName("开放班");
    request.setCourseType("ELECTIVE");
    request.setEnrollmentType("OPEN");
    request.setEligibilityType("SELECTED");
    PersonSelectionRequest selection = new PersonSelectionRequest();
    selection.setSelectionType("DEPT");
    selection.setSubjectIds(java.util.List.of(10L));
    request.setEligibilitySelection(selection);

    service.createOffering(request);

    verify(personSelectionService).resolve(selection);
    verify(eligibleUserMapper, times(2)).insert(any(com.sts.course.entity.CourseOfferingEligibleUser.class));
  }

  @Test
  @DisplayName("pageOfferings：默认同时返回独立开课与培训开课，并暴露 planContentId")
  void pageOfferings_includesTrainingAndIndependentWithPlanContentId() {
    CourseOffering independent = offering(OFFERING_ID, "PUBLISHED", VERSION_ID, 1);
    independent.setOfferingName("独立班");
    CourseOffering training = offering(101L, "PUBLISHED", 50L, 1);
    training.setOfferingName("培训班");
    training.setPlanContentId(300L);
    Page<CourseOffering> page = new Page<>(1, 10);
    page.setRecords(java.util.List.of(independent, training));
    page.setTotal(2);
    when(offeringMapper.selectPage(any(), any())).thenReturn(page);
    Course course = course(COURSE_ID, "PUBLISHED", VERSION_ID);
    course.setCourseName("安全课");
    when(courseMapper.selectList(any())).thenReturn(java.util.List.of(course));

    var result = service.pageOfferings(new PageRequest<>(1, 10, new CourseOfferingListQuery()));

    assertThat(result.total()).isEqualTo(2);
    assertThat(result.list()).extracting(item -> item.getCourseOfferingId())
      .containsExactly(OFFERING_ID, 101L);
    assertThat(result.list().getFirst().getPlanContentId()).isNull();
    assertThat(result.list().get(1).getPlanContentId()).isEqualTo(300L);
  }

  @Test
  @DisplayName("pageOfferings：可按 planContentId 过滤")
  void pageOfferings_filtersByPlanContentId() {
    Page<CourseOffering> page = new Page<>(1, 10);
    page.setRecords(java.util.List.of());
    page.setTotal(0);
    when(offeringMapper.selectPage(any(), any())).thenReturn(page);
    CourseOfferingListQuery query = new CourseOfferingListQuery();
    query.setPlanContentId(300L);

    service.pageOfferings(new PageRequest<>(1, 10, query));

    @SuppressWarnings("unchecked")
    ArgumentCaptor<LambdaQueryWrapper<CourseOffering>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
    verify(offeringMapper).selectPage(any(), captor.capture());
    assertThat(captor.getValue().getSqlSegment()).containsIgnoringCase("plan_content_id");
    assertThat(captor.getValue().getParamNameValuePairs()).containsValue(300L);
  }

  @Test
  @DisplayName("getOffering：详情返回 planContentId")
  void getOffering_exposesPlanContentId() {
    CourseOffering offering = offering(OFFERING_ID, "PUBLISHED", VERSION_ID, 1);
    offering.setPlanContentId(300L);
    when(offeringMapper.selectOne(any())).thenReturn(offering);
    Course course = course(COURSE_ID, "PUBLISHED", VERSION_ID);
    course.setCourseName("安全课");
    when(courseMapper.selectOne(any())).thenReturn(course);

    var vo = service.getOffering(OFFERING_ID);

    assertThat(vo.getPlanContentId()).isEqualTo(300L);
  }

  private static CourseOffering offering(Long id, String publishStatus, Long courseVersionId, int version) {
    CourseOffering offering = new CourseOffering();
    offering.setCourseOfferingId(id);
    offering.setOrganizationId(ORG_ID);
    offering.setCourseId(COURSE_ID);
    offering.setPublishStatus(publishStatus);
    offering.setCourseVersionId(courseVersionId);
    offering.setVersion(version);
    return offering;
  }

  private static Course course(Long courseId, String publishStatus, Long currentVersionId) {
    Course course = new Course();
    course.setCourseId(courseId);
    course.setOrganizationId(ORG_ID);
    course.setPublishStatus(publishStatus);
    course.setCurrentVersionId(currentVersionId);
    return course;
  }

  private static final String STATUS_DRAFT = "DRAFT";
}

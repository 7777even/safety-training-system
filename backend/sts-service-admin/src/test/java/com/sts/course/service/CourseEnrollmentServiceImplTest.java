package com.sts.course.service;

import cn.dev33.satoken.stp.StpUtil;
import com.sts.common.exception.BusinessException;
import com.sts.course.entity.CourseEnrollment;
import com.sts.course.entity.CourseOffering;
import com.sts.course.mapper.CourseEnrollmentMapper;
import com.sts.course.mapper.CourseOfferingMapper;
import com.sts.course.mapper.CourseOfferingEligibleUserMapper;
import com.sts.admin.tenant.TenantContext;
import com.sts.person.dto.PersonSelectionRequest;
import com.sts.person.service.PersonSelectionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CourseEnrollmentServiceImplTest {

  private static final Long ORG_ID = 1L;
  private static final Long OFFERING_ID = 100L;
  private static final Long ENROLLMENT_ID = 1000L;
  private static final Long USER_ID = 99L;
  private static final Long OTHER_USER_ID = 88L;

  private CourseEnrollmentMapper enrollmentMapper;
  private CourseOfferingMapper offeringMapper;
  private CourseOfferingEligibleUserMapper eligibleUserMapper;
  private PersonSelectionService personSelectionService;
  private CourseEnrollmentServiceImpl service;

  @BeforeEach
  void setUp() {
    TenantContext.setTenantId(ORG_ID_FOR_TENANT);
    enrollmentMapper = mock(CourseEnrollmentMapper.class);
    offeringMapper = mock(CourseOfferingMapper.class);
    eligibleUserMapper = mock(CourseOfferingEligibleUserMapper.class);
    personSelectionService = mock(PersonSelectionService.class);
    when(personSelectionService.resolve(any())).thenReturn(List.of(USER_ID));
    service = new CourseEnrollmentServiceImpl(
      enrollmentMapper, offeringMapper, mock(com.sts.admin.mapper.SysUserMapper.class),
      eligibleUserMapper, personSelectionService, org.mockito.Mockito.mock(com.sts.course.mapper.CourseVersionMapper.class), org.mockito.Mockito.mock(CourseSnapshotAssembler.class));
  }

  @AfterEach
  void clearTenantContext() {
    TenantContext.clear();
  }

  private static final Long ORG_ID_FOR_TENANT = 1L;

  @Test
  @DisplayName("assign：已发布开课创建 ASSIGN 来源报名")
  void assign_publishedOffering_createsEnrollment() {
    CourseOffering offering = offering(OFFERING_ID, "PUBLISHED", "REQUIRED", "ASSIGNED", 0);
    when(offeringMapper.selectOne(any())).thenReturn(offering);
    when(enrollmentMapper.selectOne(any())).thenReturn(null);
    doAnswer(invocation -> {
      CourseEnrollment entity = invocation.getArgument(0);
      entity.setCourseEnrollmentId(ENROLLMENT_ID);
      return 1;
    }).when(enrollmentMapper).insert(any(CourseEnrollment.class));

    service.assign(OFFERING_ID, selection("USER", List.of(USER_ID)));

    ArgumentCaptor<CourseEnrollment> captor = ArgumentCaptor.forClass(CourseEnrollment.class);
    verify(enrollmentMapper).insert(captor.capture());
    CourseEnrollment inserted = captor.getValue();
    assertThat(inserted.getEnrollmentSource()).isEqualTo("ASSIGN");
    assertThat(inserted.getLearningStatus()).isEqualTo("NOT_STARTED");
    assertThat(inserted.getProgressPercent()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(inserted.getTotalStudySeconds()).isZero();
    assertThat(inserted.getCreditAwarded()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(inserted.getEnrolledTime()).isNotNull();
  }

  @Test
  @DisplayName("assign：重复用户幂等跳过")
  void assign_duplicateUser_idempotentSkip() {
    CourseOffering offering = offering(OFFERING_ID, "PUBLISHED", "REQUIRED", "ASSIGNED", 0);
    when(offeringMapper.selectOne(any())).thenReturn(offering);
    when(enrollmentMapper.selectOne(any())).thenReturn(enrollment(ENROLLMENT_ID, USER_ID, "NOT_STARTED"));

    service.assign(OFFERING_ID, selection("USER", List.of(USER_ID)));

    verify(enrollmentMapper, never()).insert(any(CourseEnrollment.class));
  }

  @Test
  @DisplayName("assign：非 PUBLISHED 开课 → 400 / 5072")
  void assign_nonPublishedOffering_throws5072() {
    CourseOffering offering = offering(OFFERING_ID, "DRAFT", "REQUIRED", "ASSIGNED", 0);
    when(offeringMapper.selectOne(any())).thenReturn(offering);

    assertThatThrownBy(() -> service.assign(OFFERING_ID, selection("USER", List.of(USER_ID))))
      .isInstanceOf(BusinessException.class)
      .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(5072));
    verify(enrollmentMapper, never()).insert(any(CourseEnrollment.class));
  }

  @Test
  @DisplayName("selfEnroll：OPEN 且允许自助时创建 SELF 报名")
  void selfEnroll_openOffering_createsEnrollment() {
    CourseOffering offering = offering(OFFERING_ID, "PUBLISHED", "ELECTIVE", "OPEN", 1);
    when(offeringMapper.selectOne(any())).thenReturn(offering);
    when(enrollmentMapper.selectOne(any())).thenReturn(null);
    doAnswer(invocation -> {
      CourseEnrollment entity = invocation.getArgument(0);
      entity.setCourseEnrollmentId(ENROLLMENT_ID);
      return 1;
    }).when(enrollmentMapper).insert(any(CourseEnrollment.class));

    try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
      stp.when(StpUtil::getLoginIdAsLong).thenReturn(USER_ID);
      Long id = service.selfEnroll(OFFERING_ID);
      assertThat(id).isEqualTo(ENROLLMENT_ID);
    }

    ArgumentCaptor<CourseEnrollment> captor = ArgumentCaptor.forClass(CourseEnrollment.class);
    verify(enrollmentMapper).insert(captor.capture());
    assertThat(captor.getValue().getEnrollmentSource()).isEqualTo("SELF");
    assertThat(captor.getValue().getUserId()).isEqualTo(USER_ID);
  }

  @Test
  @DisplayName("selfEnroll：重复报名幂等返回已有 ID")
  void selfEnroll_duplicate_idempotentReturnExisting() {
    CourseOffering offering = offering(OFFERING_ID, "PUBLISHED", "ELECTIVE", "OPEN", 1);
    CourseEnrollment existing = enrollment(ENROLLMENT_ID, USER_ID, "NOT_STARTED");
    when(offeringMapper.selectOne(any())).thenReturn(offering);
    when(enrollmentMapper.selectOne(any())).thenReturn(existing);

    try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
      stp.when(StpUtil::getLoginIdAsLong).thenReturn(USER_ID);
      Long id = service.selfEnroll(OFFERING_ID);
      assertThat(id).isEqualTo(ENROLLMENT_ID);
    }

    verify(enrollmentMapper, never()).insert(any(CourseEnrollment.class));
  }

  @Test
  @DisplayName("selfEnroll：SELECTED 开放报名但用户不在白名单 → 4109")
  void selfEnroll_selectedEligibilityReject_throws4109() {
    CourseOffering offering = offering(OFFERING_ID, "PUBLISHED", "ELECTIVE", "OPEN", 1);
    offering.setEligibilityType("SELECTED");
    when(offeringMapper.selectOne(any())).thenReturn(offering);
    when(eligibleUserMapper.selectCount(any())).thenReturn(0L);

    try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
      stp.when(StpUtil::getLoginIdAsLong).thenReturn(USER_ID);
      assertThatThrownBy(() -> service.selfEnroll(OFFERING_ID))
        .isInstanceOf(BusinessException.class)
        .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(4109));
    }

    verify(enrollmentMapper, never()).insert(any(CourseEnrollment.class));
  }

  @Test
  @DisplayName("selfWithdraw：选修且未完成可退选")
  void selfWithdraw_electiveNotCompleted_success() {
    CourseEnrollment enrollment = enrollment(ENROLLMENT_ID, USER_ID, "LEARNING");
    CourseOffering offering = offering(OFFERING_ID, "PUBLISHED", "ELECTIVE", "OPEN", 1);
    when(enrollmentMapper.selectOne(any())).thenReturn(enrollment);
    when(offeringMapper.selectOne(any())).thenReturn(offering);

    CourseEnrollmentServiceImpl spyService = spy(service);
    doReturn(true).when(spyService).removeById(ENROLLMENT_ID);

    try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
      stp.when(StpUtil::getLoginIdAsLong).thenReturn(USER_ID);
      spyService.selfWithdraw(ENROLLMENT_ID);
    }

    verify(spyService).removeById(ENROLLMENT_ID);
  }

  @Test
  @DisplayName("selfWithdraw：必修不允许退选 → 400 / 5073")
  void selfWithdraw_requiredOffering_throws5073() {
    CourseEnrollment enrollment = enrollment(ENROLLMENT_ID, USER_ID, "NOT_STARTED");
    CourseOffering offering = offering(OFFERING_ID, "PUBLISHED", "REQUIRED", "ASSIGNED", 0);
    when(enrollmentMapper.selectOne(any())).thenReturn(enrollment);
    when(offeringMapper.selectOne(any())).thenReturn(offering);

    try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
      stp.when(StpUtil::getLoginIdAsLong).thenReturn(USER_ID);
      assertThatThrownBy(() -> service.selfWithdraw(ENROLLMENT_ID))
        .isInstanceOf(BusinessException.class)
        .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(5073));
    }

    verify(enrollmentMapper, never()).update(any(), any());
  }

  @Test
  @DisplayName("selfWithdraw：非本人报名 → 403 / 5073")
  void selfWithdraw_notOwner_throws5073() {
    CourseEnrollment enrollment = enrollment(ENROLLMENT_ID, OTHER_USER_ID, "NOT_STARTED");
    when(enrollmentMapper.selectOne(any())).thenReturn(enrollment);

    try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
      stp.when(StpUtil::getLoginIdAsLong).thenReturn(USER_ID);
      assertThatThrownBy(() -> service.selfWithdraw(ENROLLMENT_ID))
        .isInstanceOf(BusinessException.class)
        .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(5073));
    }
  }

  private static CourseOffering offering(Long id, String publishStatus, String courseType,
                                         String enrollmentType, int selfEnrollAllowed) {
    CourseOffering offering = new CourseOffering();
    offering.setCourseOfferingId(id);
    offering.setOrganizationId(ORG_ID);
    offering.setPublishStatus(publishStatus);
    offering.setCourseType(courseType);
    offering.setEnrollmentType(enrollmentType);
    offering.setEligibilityType("ALL");
    return offering;
  }

  private static PersonSelectionRequest selection(String type, List<Long> ids) {
    PersonSelectionRequest request = new PersonSelectionRequest();
    request.setSelectionType(type);
    request.setSubjectIds(ids);
    return request;
  }

  private static CourseEnrollment enrollment(Long id, Long userId, String learningStatus) {
    CourseEnrollment enrollment = new CourseEnrollment();
    enrollment.setCourseEnrollmentId(id);
    enrollment.setOrganizationId(ORG_ID);
    enrollment.setCourseOfferingId(OFFERING_ID);
    enrollment.setUserId(userId);
    enrollment.setLearningStatus(learningStatus);
    return enrollment;
  }
}

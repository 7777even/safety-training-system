package com.sts.course.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.sts.admin.support.AbstractMysqlIntegrationTest;
import com.sts.course.entity.*;
import com.sts.course.support.CourseMapperItConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sts.org.entity.Organization;
import com.sts.org.mapper.OrganizationMapper;

/**
 * {@link CourseStudySessionMapper} 集成测试（Testcontainers 真 MySQL）。
 * <p>
 * 重点验证 {@code uk_enrollment_trace_deleted(course_enrollment_id, client_trace_id, deleted)}：
 * 同报名同客户端追踪 ID 只能有一条在线会话；逻辑删除后可重新插入。
 */
@SpringBootTest(classes = CourseMapperItConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class CourseStudySessionMapperIT extends AbstractMysqlIntegrationTest {

  private static final Long USER_ID = 99L;
  private static final String TRACE_ID = "trace-it-001";

  @Autowired
  private CourseStudySessionMapper studySessionMapper;

  @Autowired
  private CourseEnrollmentMapper enrollmentMapper;

  @Autowired
  private CourseOfferingMapper offeringMapper;

  @Autowired
  private CourseMapper courseMapper;

  @Autowired
  private CourseChapterMapper chapterMapper;

  @Autowired
  private CourseItemMapper itemMapper;

  @Autowired
  private OrganizationMapper organizationMapper;

  @Test
  @DisplayName("uk_enrollment_trace_deleted：同报名同 client_trace_id 第二次插入触发唯一键冲突")
  void uniqueKey_rejectsDuplicateEnrollmentTrace() {
    EnrollmentContext ctx = createEnrollmentContext("SAFETY-SS-001", "OFF-SS-001");

    studySessionMapper.insert(studySession(ctx, TRACE_ID));

    assertThatThrownBy(() -> studySessionMapper.insert(studySession(ctx, TRACE_ID)))
      .isInstanceOf(DuplicateKeyException.class);
  }

  @Test
  @DisplayName("逻辑删除后 deleted=主键ID，同 client_trace_id 可重新插入")
  void logicalDelete_allowsReinsertSameClientTraceId() {
    EnrollmentContext ctx = createEnrollmentContext("SAFETY-SS-002", "OFF-SS-002");

    CourseStudySession original = studySession(ctx, TRACE_ID);
    studySessionMapper.insert(original);

    int deletedRows = studySessionMapper.update(null,
                                                Wrappers.<CourseStudySession>lambdaUpdate()
                                                  .setSql("deleted = study_session_id")
                                                  .eq(CourseStudySession::getStudySessionId,
                                                      original.getStudySessionId()));
    assertThat(deletedRows).isEqualTo(1);

    CourseStudySession reinserted = studySession(ctx, TRACE_ID);
    studySessionMapper.insert(reinserted);

    CourseStudySession found = studySessionMapper.selectOne(
      new LambdaQueryWrapper<CourseStudySession>()
        .eq(CourseStudySession::getCourseEnrollmentId, ctx.enrollmentId())
        .eq(CourseStudySession::getClientTraceId, TRACE_ID)
        .eq(CourseStudySession::getDeleted, 0L));
    assertThat(found).isNotNull();
    assertThat(found.getStudySessionId()).isEqualTo(reinserted.getStudySessionId());
  }

  @Test
  @DisplayName("selectOtherActiveContext：其他上下文活跃心跳命中；同上下文、显式收尾、心跳过期均不命中")
  void selectOtherActiveContext_matchesOnlyFreshHeartbeatOfOtherContext() {
    EnrollmentContext ctx = createEnrollmentContext("SAFETY-SS-MUTEX-001", "OFF-SS-MUTEX-001");
    Long otherItemId = createCourseItem(ctx.courseId());
    LocalDateTime since = LocalDateTime.now().minusSeconds(90);

    studySessionMapper.insert(activeSession(ctx, ctx.courseItemId(), USER_ID, "device-a", "HEARTBEAT", null));

    // 其他内容项视为其他上下文：命中。
    CourseStudySession otherContext = studySessionMapper.selectOtherActiveContext(
      USER_ID, ctx.enrollmentId(), otherItemId, "device-a", since);
    assertThat(otherContext).isNotNull();
    assertThat(otherContext.getCourseItemId()).isEqualTo(ctx.courseItemId());

    // 同一上下文（同报名、同内容项、同终端）不命中。
    assertThat(studySessionMapper.selectOtherActiveContext(
      USER_ID, ctx.enrollmentId(), ctx.courseItemId(), "device-a", since)).isNull();

    // 最新一行为显式收尾（USER_EXIT）：上下文已释放，不命中。
    studySessionMapper.insert(activeSession(ctx, ctx.courseItemId(), USER_ID, "device-a", "USER_EXIT", null));
    assertThat(studySessionMapper.selectOtherActiveContext(
      USER_ID, ctx.enrollmentId(), otherItemId, "device-a", since)).isNull();

    // 心跳超过 90 秒窗口：视为会话结束，不命中。
    studySessionMapper.insert(activeSession(ctx, ctx.courseItemId(), USER_ID, "device-a", "HEARTBEAT",
      LocalDateTime.now().minusMinutes(5)));
    assertThat(studySessionMapper.selectOtherActiveContext(
      USER_ID, ctx.enrollmentId(), otherItemId, "device-a", since)).isNull();
  }

  @Test
  @DisplayName("selectOtherUserActiveOnDevice：同终端其他账户活跃心跳命中；同账户与过期不命中")
  void selectOtherUserActiveOnDevice_matchesOnlyOtherUserFreshHeartbeat() {
    EnrollmentContext ctx = createEnrollmentContext("SAFETY-SS-MUTEX-002", "OFF-SS-MUTEX-002");
    Long otherUserId = USER_ID + 1;
    LocalDateTime since = LocalDateTime.now().minusSeconds(90);

    studySessionMapper.insert(activeSession(ctx, ctx.courseItemId(), otherUserId, "device-b", "HEARTBEAT", null));

    assertThat(studySessionMapper.selectOtherUserActiveOnDevice("device-b", USER_ID, since)).isNotNull();
    // 同一账户不构成终端多账户互斥。
    assertThat(studySessionMapper.selectOtherUserActiveOnDevice("device-b", otherUserId, since)).isNull();

    studySessionMapper.insert(activeSession(ctx, ctx.courseItemId(), otherUserId, "device-b", "HEARTBEAT",
      LocalDateTime.now().minusMinutes(5)));
    assertThat(studySessionMapper.selectOtherUserActiveOnDevice("device-b", USER_ID, since)).isNull();
  }

  private EnrollmentContext createEnrollmentContext(String courseCode, String offeringCode) {
    Long orgId = defaultOrganizationId();
    Long courseId = createCourse(orgId, courseCode);
    Long courseItemId = createCourseItem(courseId);
    Long offeringId = createOffering(orgId, courseId, offeringCode);
    Long enrollmentId = createEnrollment(orgId, offeringId);
    return new EnrollmentContext(orgId, courseId, enrollmentId, courseItemId);
  }

  private Long defaultOrganizationId() {
    Organization organization = organizationMapper.selectByOrgCode("DEMO");
    assertThat(organization).isNotNull();
    return organization.getOrganizationId();
  }

  private Long createCourse(Long orgId, String code) {
    Course course = new Course();
    course.setOrganizationId(orgId);
    course.setCourseCode(code);
    course.setCourseName("安全教育课程");
    course.setContentMode("MULTI_CHAPTER");
    course.setCredit(BigDecimal.ONE);
    course.setPointMode("NONE");
    course.setPointValue(BigDecimal.ZERO);
    course.setSourceDownloadMode("NONE");
    course.setIsSpeedPlayAllowed(0);
    course.setIsStudyAnticheatEnabled(0);
    course.setIsStudyDescriptionEnabled(1);
    course.setIsWatermarkEnabled(0);
    course.setPublishStatus("DRAFT");
    course.setStatus(1);
    courseMapper.insert(course);
    return course.getCourseId();
  }

  private Long createCourseItem(Long courseId) {
    CourseChapter chapter = new CourseChapter();
    chapter.setCourseId(courseId);
    chapter.setParentId(0L);
    chapter.setAncestors("0");
    chapter.setChapterName("第一章");
    chapter.setSortOrder(0);
    chapter.setStatus(1);
    chapterMapper.insert(chapter);

    CourseItem item = new CourseItem();
    item.setCourseId(courseId);
    item.setChapterId(chapter.getChapterId());
    item.setItemType("ASSET");
    item.setItemTitle("视频课");
    item.setIsRequired(1);
    item.setMinimumStudySeconds(60);
    item.setSortOrder(0);
    item.setIsLockedAfterParticipation(0);
    item.setStatus(1);
    itemMapper.insert(item);
    return item.getCourseItemId();
  }

  private Long createOffering(Long orgId, Long courseId, String offeringCode) {
    CourseOffering offering = new CourseOffering();
    offering.setOrganizationId(orgId);
    offering.setOfferingCode(offeringCode);
    offering.setCourseId(courseId);
    offering.setOfferingName("春季班");
    offering.setCourseType("REQUIRED");
    offering.setEnrollmentType("ASSIGNED");
    offering.setCompletionRule("ALL_REQUIRED_ITEMS");
    offering.setEligibilityType("ALL");
    offering.setPublishStatus("DRAFT");
    offering.setStatus(1);
    offeringMapper.insert(offering);
    return offering.getCourseOfferingId();
  }

  private Long createEnrollment(Long orgId, Long offeringId) {
    CourseEnrollment enrollment = new CourseEnrollment();
    enrollment.setOrganizationId(orgId);
    enrollment.setCourseOfferingId(offeringId);
    enrollment.setUserId(USER_ID);
    enrollment.setEnrollmentSource("ASSIGN");
    enrollment.setEnrolledTime(LocalDateTime.now());
    enrollment.setLearningStatus("NOT_STARTED");
    enrollment.setProgressPercent(BigDecimal.ZERO);
    enrollment.setTotalStudySeconds(0);
    enrollment.setCreditAwarded(BigDecimal.ZERO);
    enrollmentMapper.insert(enrollment);
    return enrollment.getCourseEnrollmentId();
  }

  private static CourseStudySession studySession(EnrollmentContext ctx, String clientTraceId) {
    CourseStudySession session = new CourseStudySession();
    session.setOrganizationId(ctx.orgId());
    session.setCourseEnrollmentId(ctx.enrollmentId());
    session.setCourseItemId(ctx.courseItemId());
    session.setUserId(USER_ID);
    session.setDeviceType("PC");
    session.setStartedTime(LocalDateTime.now());
    session.setValidStudySeconds(60);
    session.setEndReason("USER_EXIT");
    session.setClientTraceId(clientTraceId);
    return session;
  }

  /** 互斥检测用例的会话行：可指定内容项、账户、终端、结束原因与更新时间（服务端时钟）。 */
  private static CourseStudySession activeSession(EnrollmentContext ctx, Long courseItemId, Long userId,
      String deviceId, String endReason, LocalDateTime updateTime) {
    CourseStudySession session = new CourseStudySession();
    session.setOrganizationId(ctx.orgId());
    session.setCourseEnrollmentId(ctx.enrollmentId());
    session.setCourseItemId(courseItemId);
    session.setUserId(userId);
    session.setDeviceType("PC");
    session.setDeviceId(deviceId);
    session.setStartedTime(LocalDateTime.now().minusMinutes(1));
    session.setValidStudySeconds(30);
    session.setEndReason(endReason);
    session.setClientTraceId("trace-it-" + deviceId + "-" + userId + "-" + courseItemId + "-" + endReason
      + "-" + System.nanoTime());
    if (updateTime != null) {
      session.setUpdateTime(updateTime);
    }
    return session;
  }

  private record EnrollmentContext(Long orgId, Long courseId, Long enrollmentId, Long courseItemId) {
  }
}

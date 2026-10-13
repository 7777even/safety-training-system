package com.sts.course.service;

import com.sts.admin.support.AbstractMysqlIntegrationTest;
import com.sts.admin.tenant.TenantContext;
import com.sts.course.dto.CourseAssessmentResultEffective;
import com.sts.course.entity.*;
import com.sts.course.mapper.*;
import com.sts.course.support.CourseItFixtures;
import com.sts.course.support.CourseMapperItConfig;
import com.sts.exam.entity.AssessmentExecution;
import com.sts.exam.service.AssessmentExecutionService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

/** 两个真实事务重复结算同一答卷，只认可一次学时并触发一次完成发证。 */
@SpringBootTest(classes = CourseMapperItConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class CourseSettlementConcurrencyIT extends AbstractMysqlIntegrationTest {
  @Autowired
  CourseMapper courses;
  @Autowired
  CourseVersionMapper versions;
  @Autowired
  CourseOfferingMapper offerings;
  @Autowired
  CourseEnrollmentMapper enrollments;
  @Autowired
  CourseItemProgressMapper progresses;
  @Autowired
  CourseAssessmentResultMapper results;
  @Autowired
  PlatformTransactionManager transactionManager;
  @Autowired
  JdbcTemplate jdbc;

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void effectiveCallbacksWithExistingSnapshotSerializeCompletion(boolean duplicate) throws Exception {
    long tenant = TenantContext.requireTenantId();
    var course = CourseItFixtures.newCourse(tenant, CourseItFixtures.uniqueCourseCode("B6"));
    courses.insert(course);
    var version = new CourseVersion();
    version.setCourseId(course.getCourseId());
    version.setVersionNo(1);
    version.setSnapshotHash("batch6-concurrency");
    version.setPublishedBy(1L);
    version.setPublishedTime(LocalDateTime.now());
    version.setContentSnapshotJson("""
        {"schemaVersion":2,"course":{"courseId":%d},"chapters":[{"chapterId":1,"parentId":0,"status":1,"items":[
        {"courseItemId":101,"itemType":"QUIZ","isFinalAssessment":1,"isRequired":1,"status":1},
        {"courseItemId":102,"itemType":"ASSET","isRequired":1,"status":1},
        {"courseItemId":103,"itemType":"QUIZ","isRequired":1,"status":%d}]}]}
        """.formatted(course.getCourseId(), duplicate ? 0 : 1));
    versions.insert(version);
    var offering = new CourseOffering();
    offering.setOrganizationId(tenant);
    offering.setCourseId(course.getCourseId());
    offering.setCourseVersionId(version.getCourseVersionId());
    offering.setOfferingCode(CourseItFixtures.uniqueCourseCode("B6-O"));
    offering.setOfferingName("结业并发结算");
    offering.setCourseType("ELECTIVE");
    offering.setEnrollmentType("OPEN");
    offering.setEligibilityType("ALL");
    offering.setCompletionRule("ALL_REQUIRED_ITEMS");
    offering.setPublishStatus("PUBLISHED");
    offering.setStatus(1);
    offerings.insert(offering);
    var enrollment = new CourseEnrollment();
    enrollment.setOrganizationId(tenant);
    enrollment.setCourseOfferingId(offering.getCourseOfferingId());
    enrollment.setUserId(987699L);
    enrollment.setEnrollmentSource("SELF");
    enrollment.setEnrolledTime(LocalDateTime.now());
    enrollment.setLearningStatus("LEARNING");
    enrollment.setProgressPercent(BigDecimal.ZERO);
    enrollment.setTotalStudySeconds(180);
    enrollment.setRecognizedStudySeconds(0);
    enrollment.setCreditAwarded(BigDecimal.ZERO);
    enrollments.insert(enrollment);
    var material = new CourseItemProgress();
    material.setCourseEnrollmentId(enrollment.getCourseEnrollmentId());
    material.setCourseItemId(102L);
    material.setProgressStatus("COMPLETED");
    material.setStudySeconds(180);
    material.setAttemptCount(1);
    progresses.insert(material);
    var snapshot = new CourseSnapshotAssembler(mock(CourseCourseCategoryMapper.class), mock(CourseChapterMapper.class),
        mock(CourseItemMapper.class), mock(CourseDownloadAssetMapper.class),
        mock(com.sts.asset.mapper.AssetMapper.class),
        mock(CourseItemService.class), mock(com.sts.asset.service.AssetReferenceService.class),
        mock(CourseItemQuestionBankMapper.class),
        mock(com.sts.question.mapper.QuestionBankMapper.class), mock(com.sts.paper.mapper.PaperVersionMapper.class),
        mock(com.sts.paper.mapper.PaperMapper.class), mock(TeacherFollowMapper.class));
    var executions = mock(AssessmentExecutionService.class);
    var execution = new AssessmentExecution();
    execution.setAssessmentExecutionId(987699L);
    execution.setSourceType("COURSE_QUIZ");
    when(executions.requireCourse(any(), any(), any())).thenAnswer(inv -> {
      var current = new AssessmentExecution();
      current.setAssessmentExecutionId((Long) inv.getArgument(2) == 103L ? 987700L : 987699L);
      current.setSourceType("COURSE_QUIZ"); return current;
    });
    var certificates = mock(com.sts.certificate.service.CertificateAutoIssueService.class);
    var service = new CourseLearningServiceImpl(enrollments, offerings, versions, progresses,
        mock(CourseStudySessionMapper.class), mock(CourseEvaluationMapper.class),
        mock(com.sts.admin.mapper.SysUserMapper.class), snapshot,
        mock(CourseOfferingEligibleUserMapper.class), mock(LearnerCourseResourceAssembler.class), executions, results,
        mock(com.sts.exam.mapper.ExamCandidateMapper.class), mock(com.sts.exam.mapper.ExamAttemptMapper.class),
        mock(org.springframework.context.ApplicationEventPublisher.class), certificates,
        mock(CourseCategoryService.class));
    var fact = new CourseAssessmentResultEffective(tenant, 987699L, 987699L, 987699L, 987699L,
        enrollment.getCourseEnrollmentId(), offering.getCourseOfferingId(), 101L, "COURSE_QUIZ", 2,
        LocalDateTime.now(), new BigDecimal("80"), 1, 8, 2);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var ready = new CountDownLatch(2);
      var start = new CountDownLatch(1);
      var otherFact = new CourseAssessmentResultEffective(tenant, 987700L, 987700L, 987700L, 987699L,
          enrollment.getCourseEnrollmentId(), offering.getCourseOfferingId(), 103L, "COURSE_QUIZ", 1,
          LocalDateTime.now(), new BigDecimal("80"), 1, 8, 2);
      var calls = new java.util.concurrent.atomic.AtomicInteger();
      Callable<Void> settle = () -> {
        TenantContext.setTenantId(tenant);
        try {
          var callback = duplicate || calls.getAndIncrement() == 0 ? fact : otherFact;
          new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            // 模拟考试提交事务已经建立RR快照后才进入课程结算。
            enrollments.selectById(enrollment.getCourseEnrollmentId());
            progresses.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CourseItemProgress>()
                .eq(CourseItemProgress::getCourseEnrollmentId, enrollment.getCourseEnrollmentId()));
            ready.countDown();
            try { start.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
            service.applyEffectiveAssessmentResult(callback);
          });
          return null;
        } finally {
          TenantContext.clear();
        }
      };
      var first = executor.submit(settle);
      var second = executor.submit(settle);
      boolean bothReady = ready.await(10, TimeUnit.SECONDS);
      start.countDown();
      assertThat(bothReady).isTrue();
      first.get(20, TimeUnit.SECONDS);
      second.get(20, TimeUnit.SECONDS);
      assertThat(enrollments.selectById(enrollment.getCourseEnrollmentId()).getRecognizedStudySeconds()).isEqualTo(180);
      assertThat(enrollments.selectById(enrollment.getCourseEnrollmentId()).getLearningStatus()).isEqualTo("COMPLETED");
      verify(certificates, times(1)).issueForSource("COURSE", course.getCourseId(), 987699L,
          enrollment.getCourseEnrollmentId());
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sts_course_assessment_result WHERE course_enrollment_id=?",
          Integer.class,
          enrollment.getCourseEnrollmentId())).isEqualTo(duplicate ? 1 : 2);
    } finally {
      jdbc.update("DELETE FROM sts_course_assessment_result WHERE course_enrollment_id=?",
          enrollment.getCourseEnrollmentId());
      jdbc.update("DELETE FROM sts_course_item_progress WHERE course_enrollment_id=?",
          enrollment.getCourseEnrollmentId());
      jdbc.update("DELETE FROM sts_course_enrollment WHERE course_enrollment_id=?", enrollment.getCourseEnrollmentId());
      jdbc.update("DELETE FROM sts_course_offering WHERE course_offering_id=?", offering.getCourseOfferingId());
      jdbc.update("DELETE FROM sts_course_version WHERE course_version_id=?", version.getCourseVersionId());
      jdbc.update("DELETE FROM sts_course_course WHERE course_id=?", course.getCourseId());
    }
  }
}

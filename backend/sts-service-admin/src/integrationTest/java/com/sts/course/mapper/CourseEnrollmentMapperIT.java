package com.sts.course.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sts.admin.support.AbstractMysqlIntegrationTest;
import com.sts.course.entity.Course;
import com.sts.course.entity.CourseEnrollment;
import com.sts.course.entity.CourseOffering;
import com.sts.org.entity.Organization;
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

import com.sts.org.mapper.OrganizationMapper;

/**
 * {@link CourseEnrollmentMapper} 集成测试（Testcontainers 真 MySQL）。
 * <p>
 * 重点验证 {@code uk_offering_user_deleted(course_offering_id, user_id, deleted)}：
 * 同开课同用户只能有一条在线报名；逻辑删除后可重新插入。
 */
@SpringBootTest(classes = CourseMapperItConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class CourseEnrollmentMapperIT extends AbstractMysqlIntegrationTest {

  private static final Long USER_ID = 99L;

  @Autowired
  private CourseEnrollmentMapper enrollmentMapper;

  @Autowired
  private CourseOfferingMapper offeringMapper;

  @Autowired
  private CourseMapper courseMapper;

  @Autowired
  private OrganizationMapper organizationMapper;

  @Test
  @DisplayName("uk_offering_user_deleted：同开课同用户第二次插入触发唯一键冲突")
  void uniqueKey_rejectsDuplicateOfferingUser() {
    Long offeringId = createOffering("SAFETY-ENR-001", "OFF-ENR-001");

    enrollmentMapper.insert(enrollment(offeringId, USER_ID));

    assertThatThrownBy(() -> enrollmentMapper.insert(enrollment(offeringId, USER_ID)))
      .isInstanceOf(DuplicateKeyException.class);
  }

  @Test
  @DisplayName("逻辑删除后 deleted=主键ID，同开课同用户可重新插入")
  void logicalDelete_allowsReinsertSameOfferingUser() {
    Long offeringId = createOffering("SAFETY-ENR-002", "OFF-ENR-002");

    CourseEnrollment original = enrollment(offeringId, USER_ID);
    enrollmentMapper.insert(original);

    int deletedRows = enrollmentMapper.update(null,
                                              Wrappers.<CourseEnrollment>lambdaUpdate()
                                                .setSql("deleted = course_enrollment_id")
                                                .eq(CourseEnrollment::getCourseEnrollmentId,
                                                    original.getCourseEnrollmentId()));
    assertThat(deletedRows).isEqualTo(1);

    CourseEnrollment reinserted = enrollment(offeringId, USER_ID);
    enrollmentMapper.insert(reinserted);

    CourseEnrollment found = enrollmentMapper.selectOne(
      new LambdaQueryWrapper<CourseEnrollment>()
        .eq(CourseEnrollment::getCourseOfferingId, offeringId)
        .eq(CourseEnrollment::getUserId, USER_ID)
        .eq(CourseEnrollment::getDeleted, 0L));
    assertThat(found).isNotNull();
    assertThat(found.getCourseEnrollmentId()).isEqualTo(reinserted.getCourseEnrollmentId());
  }

  @Test
  @DisplayName("我的课程分页按当前用户和学习状态隔离")
  void learnerPage_filtersCurrentUserAndLearningStatus() {
    Long offeringId = createOffering("SAFETY-ENR-PAGE", "OFF-ENR-PAGE");
    CourseEnrollment mine = enrollment(offeringId, USER_ID);
    mine.setLearningStatus("LEARNING");
    enrollmentMapper.insert(mine);
    enrollmentMapper.insert(enrollment(offeringId, USER_ID + 1));

    Page<CourseEnrollment> page = enrollmentMapper.selectPage(
      new Page<>(1, 20),
      new LambdaQueryWrapper<CourseEnrollment>()
        .eq(CourseEnrollment::getOrganizationId, defaultOrganizationId())
        .eq(CourseEnrollment::getUserId, USER_ID)
        .eq(CourseEnrollment::getLearningStatus, "LEARNING"));

    assertThat(page.getTotal()).isEqualTo(1);
    assertThat(page.getRecords()).singleElement()
      .extracting(CourseEnrollment::getCourseEnrollmentId)
      .isEqualTo(mine.getCourseEnrollmentId());
  }

  private Long createOffering(String courseCode, String offeringCode) {
    Long orgId = defaultOrganizationId();
    Long courseId = createCourse(orgId, courseCode);
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

  private CourseEnrollment enrollment(Long offeringId, Long userId) {
    CourseEnrollment enrollment = new CourseEnrollment();
    enrollment.setOrganizationId(defaultOrganizationId());
    enrollment.setCourseOfferingId(offeringId);
    enrollment.setUserId(userId);
    enrollment.setEnrollmentSource("ASSIGN");
    enrollment.setEnrolledTime(LocalDateTime.now());
    enrollment.setLearningStatus("NOT_STARTED");
    enrollment.setProgressPercent(BigDecimal.ZERO);
    enrollment.setTotalStudySeconds(0);
    enrollment.setCreditAwarded(BigDecimal.ZERO);
    return enrollment;
  }
}

package com.sts.course.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.sts.admin.support.AbstractMysqlIntegrationTest;
import com.sts.course.entity.Course;
import com.sts.course.entity.CourseOffering;
import com.sts.course.support.CourseMapperItConfig;
import com.sts.org.entity.Organization;
import com.sts.org.mapper.OrganizationMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link CourseOfferingMapper} 集成测试（Testcontainers 真 MySQL）。
 * <p>
 * 重点验证 {@code uk_org_offering_code_deleted(organization_id, offering_code, deleted)}：
 * 同机构同开课编码只能有一条在线记录；逻辑删除后可重新插入。
 */
@SpringBootTest(classes = CourseMapperItConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class CourseOfferingMapperIT extends AbstractMysqlIntegrationTest {

  @Autowired
  private CourseOfferingMapper offeringMapper;

  @Autowired
  private CourseMapper courseMapper;

  @Autowired
  private OrganizationMapper organizationMapper;

  @Test
  @DisplayName("uk_org_offering_code_deleted：同机构同开课编码第二次插入触发唯一键冲突")
  void uniqueKey_rejectsDuplicateOfferingCode() {
    Long orgId = defaultOrganizationId();
    Long courseId = createCourse(orgId, "SAFETY-OFF-001");

    offeringMapper.insert(offering(orgId, courseId, "OFF-IT-001"));

    assertThatThrownBy(() -> offeringMapper.insert(offering(orgId, courseId, "OFF-IT-001")))
      .isInstanceOf(DuplicateKeyException.class);
  }

  @Test
  @DisplayName("逻辑删除后 deleted=主键ID，同开课编码可重新插入")
  void logicalDelete_allowsReinsertSameOfferingCode() {
    Long orgId = defaultOrganizationId();
    Long courseId = createCourse(orgId, "SAFETY-OFF-002");

    CourseOffering original = offering(orgId, courseId, "OFF-IT-002");
    offeringMapper.insert(original);

    int deletedRows = offeringMapper.update(null,
                                            Wrappers.<CourseOffering>lambdaUpdate()
                                              .setSql("deleted = course_offering_id")
                                              .eq(CourseOffering::getCourseOfferingId, original.getCourseOfferingId()));
    assertThat(deletedRows).isEqualTo(1);

    CourseOffering reinserted = offering(orgId, courseId, "OFF-IT-002");
    offeringMapper.insert(reinserted);

    CourseOffering found = offeringMapper.selectOne(
      new LambdaQueryWrapper<CourseOffering>()
        .eq(CourseOffering::getOrganizationId, orgId)
        .eq(CourseOffering::getOfferingCode, "OFF-IT-002")
        .eq(CourseOffering::getDeleted, 0L));
    assertThat(found).isNotNull();
    assertThat(found.getCourseOfferingId()).isEqualTo(reinserted.getCourseOfferingId());
  }

  @Test
  @DisplayName("uk_plan_content_deleted：同一计划内容只能有一条在线开课")
  void uniquePlanContentId_rejectsDuplicateTrainingOffering() {
    Long orgId = defaultOrganizationId();
    Long courseId = createCourse(orgId, "SAFETY-OFF-PC");
    CourseOffering first = offering(orgId, courseId, "OFF-PC-001");
    first.setPlanContentId(88001L);
    offeringMapper.insert(first);
    CourseOffering second = offering(orgId, courseId, "OFF-PC-002");
    second.setPlanContentId(88001L);
    assertThatThrownBy(() -> offeringMapper.insert(second)).isInstanceOf(DuplicateKeyException.class);

    CourseOffering unboundA = offering(orgId, courseId, "OFF-PC-NULL-A");
    CourseOffering unboundB = offering(orgId, courseId, "OFF-PC-NULL-B");
    offeringMapper.insert(unboundA);
    offeringMapper.insert(unboundB);
    assertThat(unboundA.getCourseOfferingId()).isNotNull();
    assertThat(unboundB.getCourseOfferingId()).isNotNull();
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

  private static CourseOffering offering(Long orgId, Long courseId, String offeringCode) {
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
    return offering;
  }
}

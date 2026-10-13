package com.sts.course.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.sts.admin.support.AbstractMysqlIntegrationTest;
import com.sts.course.entity.Course;
import com.sts.org.entity.Organization;
import com.sts.course.support.CourseMapperItConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sts.org.mapper.OrganizationMapper;

/**
 * {@link CourseMapper} 集成测试（Testcontainers 真 MySQL）。
 * <p>
 * 重点验证 {@code uk_org_course_code_deleted} 唯一索引：同机构同编码只能有一条在线记录；
 * 逻辑删除（{@code deleted=主键ID}）后编码可复用。
 */
@SpringBootTest(classes = CourseMapperItConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class CourseMapperIT extends AbstractMysqlIntegrationTest {

  @Autowired
  private CourseMapper courseMapper;

  @Autowired
  private OrganizationMapper organizationMapper;

  @Test
  @DisplayName("uk_org_course_code_deleted：同机构同编码第二次插入触发唯一键冲突")
  void uniqueKey_rejectsDuplicateCourseCode() {
    Long orgId = defaultOrganizationId();

    courseMapper.insert(course(orgId, "SAFETY-IT-001"));

    assertThatThrownBy(() -> courseMapper.insert(course(orgId, "SAFETY-IT-001")))
      .isInstanceOf(DuplicateKeyException.class);
  }

  @Test
  @DisplayName("逻辑删除后 deleted=主键ID，同编码可重新插入")
  void logicalDelete_allowsReinsertSameCode() {
    Long orgId = defaultOrganizationId();

    Course original = course(orgId, "SAFETY-IT-002");
    courseMapper.insert(original);

    int deletedRows = courseMapper.update(null,
                                          Wrappers.<Course>lambdaUpdate()
                                            .setSql("deleted = course_id")
                                            .eq(Course::getCourseId, original.getCourseId()));
    assertThat(deletedRows).isEqualTo(1);

    Course reinserted = course(orgId, "SAFETY-IT-002");
    courseMapper.insert(reinserted);

    Course found = courseMapper.selectOne(
      new LambdaQueryWrapper<Course>()
        .eq(Course::getOrganizationId, orgId)
        .eq(Course::getCourseCode, "SAFETY-IT-002")
        .eq(Course::getDeleted, 0L));
    assertThat(found).isNotNull();
    assertThat(found.getCourseId()).isEqualTo(reinserted.getCourseId());
  }

  private Long defaultOrganizationId() {
    Organization organization = organizationMapper.selectByOrgCode("DEMO");
    assertThat(organization).isNotNull();
    return organization.getOrganizationId();
  }

  private static Course course(Long orgId, String code) {
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
    return course;
  }
}

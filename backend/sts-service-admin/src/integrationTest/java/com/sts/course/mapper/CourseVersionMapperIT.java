package com.sts.course.mapper;

import com.sts.admin.support.AbstractMysqlIntegrationTest;
import com.sts.course.entity.Course;
import com.sts.course.entity.CourseVersion;
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
 * {@link CourseVersionMapper} 集成测试（Testcontainers 真 MySQL）。
 * <p>
 * 重点验证 {@code uk_course_version(course_id, version_no)}：同课程同版本号不可重复插入。
 * CourseVersion 为审计 C 类（无 deleted 字段），仅验证重复插入冲突。
 */
@SpringBootTest(classes = CourseMapperItConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class CourseVersionMapperIT extends AbstractMysqlIntegrationTest {

  @Autowired
  private CourseVersionMapper versionMapper;

  @Autowired
  private CourseMapper courseMapper;

  @Autowired
  private OrganizationMapper organizationMapper;

  @Test
  @DisplayName("uk_course_version：同课程同 version_no 第二次插入触发唯一键冲突")
  void uniqueKey_rejectsDuplicateVersionNo() {
    Long courseId = createCourse("SAFETY-VER-001");

    versionMapper.insert(version(courseId, 1));

    assertThatThrownBy(() -> versionMapper.insert(version(courseId, 1)))
      .isInstanceOf(DuplicateKeyException.class);
  }

  private Long createCourse(String code) {
    Organization organization = organizationMapper.selectByOrgCode("DEMO");
    assertThat(organization).isNotNull();

    Course course = new Course();
    course.setOrganizationId(organization.getOrganizationId());
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

  private static CourseVersion version(Long courseId, int versionNo) {
    CourseVersion version = new CourseVersion();
    version.setCourseId(courseId);
    version.setVersionNo(versionNo);
    version.setContentSnapshotJson("{\"schemaVersion\":1}");
    version.setSnapshotHash("it-hash-" + versionNo);
    version.setPublishedBy(1L);
    version.setPublishedTime(LocalDateTime.now());
    return version;
  }
}

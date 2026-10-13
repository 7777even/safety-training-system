package com.sts.course.service;

import com.sts.admin.support.AbstractMysqlIntegrationTest;
import com.sts.common.service.BaseServiceImpl;
import com.sts.course.entity.Course;
import com.sts.course.mapper.CourseMapper;
import com.sts.org.mapper.OrganizationMapper;
import com.sts.course.support.CourseItFixtures;
import com.sts.course.support.CourseMapperItConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 课程逻辑删除集成测试（对齐 {@code LogicDeleteServiceIT}）。
 */
@SpringBootTest(classes = {CourseMapperItConfig.class, CourseLogicDeleteServiceIT.ServiceConfig.class},
  webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class CourseLogicDeleteServiceIT extends AbstractMysqlIntegrationTest {

  @Autowired
  private ItCourseService courseService;
  @Autowired
  private CourseMapper courseMapper;
  @Autowired
  private OrganizationMapper organizationMapper;
  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Test
  @DisplayName("removeById：deleted 落库为主键ID，且 selectById 因 @TableLogic 查不到")
  void removeById_setsDeletedToPrimaryKey_andHidesRow() {
    Long orgId = CourseItFixtures.defaultOrganizationId(organizationMapper);
    Course course = CourseItFixtures.newCourse(orgId, CourseItFixtures.uniqueCourseCode("IT-DEL-"));
    courseService.save(course);
    Long id = course.getCourseId();

    assertThat(courseService.removeById(id)).isTrue();

    Long rawDeleted = jdbcTemplate.queryForObject(
      "select deleted from sts_course_course where course_id = ?", Long.class, id);
    assertThat(rawDeleted).isEqualTo(id);
    assertThat(courseMapper.selectById(id)).isNull();
  }

  @Test
  @DisplayName("软删后可用同一 course_code 重新插入")
  void reinsertSameCourseCode_afterSoftDelete_succeeds() {
    Long orgId = CourseItFixtures.defaultOrganizationId(organizationMapper);
    String code = CourseItFixtures.uniqueCourseCode("IT-REUSE-");

    Course first = CourseItFixtures.newCourse(orgId, code);
    courseService.save(first);
    courseService.removeById(first.getCourseId());

    Course second = CourseItFixtures.newCourse(orgId, code);
    courseService.save(second);

    assertThat(second.getCourseId()).isNotNull().isNotEqualTo(first.getCourseId());
    Course active = courseMapper.selectById(second.getCourseId());
    assertThat(active).isNotNull();
    assertThat(active.getCourseName()).isEqualTo("安全教育课程");
  }

  static class ItCourseService extends BaseServiceImpl<CourseMapper, Course> {
  }

  @TestConfiguration
  static class ServiceConfig {
    @Bean
    ItCourseService itCourseService() {
      return new ItCourseService();
    }
  }
}

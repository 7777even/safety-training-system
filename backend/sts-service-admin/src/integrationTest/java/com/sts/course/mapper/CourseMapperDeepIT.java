package com.sts.course.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sts.admin.support.AbstractMysqlIntegrationTest;
import com.sts.common.exception.BusinessException;
import com.sts.course.entity.Course;
import com.sts.course.support.CourseItFixtures;
import com.sts.course.support.CourseMapperItConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sts.org.mapper.OrganizationMapper;

/**
 * {@link CourseMapper} 深度集成测试：insert 往返、分页拦截器、乐观锁（对齐 {@code SysUserMapperIT}）。
 */
@SpringBootTest(classes = CourseMapperItConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class CourseMapperDeepIT extends AbstractMysqlIntegrationTest {

  @Autowired
  private CourseMapper courseMapper;

  @Autowired
  private OrganizationMapper organizationMapper;

  @Test
  @DisplayName("insert + selectById：真库往返，自增主键回填")
  void insertThenSelectById_roundTrips() {
    Long orgId = CourseItFixtures.defaultOrganizationId(organizationMapper);
    Course course = CourseItFixtures.newCourse(orgId, CourseItFixtures.uniqueCourseCode("IT-RT-"));

    courseMapper.insert(course);

    assertThat(course.getCourseId()).isNotNull();
    Course found = courseMapper.selectById(course.getCourseId());
    assertThat(found).isNotNull();
    assertThat(found.getCourseCode()).isEqualTo(course.getCourseCode());
    assertThat(found.getCreateTime()).isNotNull();
    assertThat(found.getDeleted()).isEqualTo(0L);
    assertThat(found.getVersion()).isZero();
  }

  @Test
  @DisplayName("selectPage：PaginationInnerInterceptor 真的分页（total 与 records 都对）")
  void selectPage_appliesPaginationInterceptor() {
    Long orgId = CourseItFixtures.defaultOrganizationId(organizationMapper);
    for (int i = 0; i < 5; i++) {
      courseMapper.insert(CourseItFixtures.newCourse(orgId, "it_page_" + i));
    }

    Page<Course> page = courseMapper.selectPage(
      Page.of(1, 2),
      new LambdaQueryWrapper<Course>()
        .eq(Course::getOrganizationId, orgId)
        .likeRight(Course::getCourseCode, "it_page_"));

    assertThat(page.getTotal()).isEqualTo(5);
    assertThat(page.getRecords()).hasSize(2);
    assertThat(page.getPages()).isEqualTo(3);
  }

  @Test
  @DisplayName("updateById 乐观锁：过期版本的并发更新影响 0 行")
  void updateById_staleVersionAffectsZeroRows() {
    Long orgId = CourseItFixtures.defaultOrganizationId(organizationMapper);
    Course course = CourseItFixtures.newCourse(orgId, CourseItFixtures.uniqueCourseCode("IT-LOCK-"));
    courseMapper.insert(course);
    Long id = course.getCourseId();

    Course writerA = courseMapper.selectById(id);
    assertThat(writerA.getVersion()).isZero();
    writerA.setCourseName("A-first");
    assertThat(courseMapper.updateById(writerA)).isEqualTo(1);

    Course writerB = staleCopy(id, "B-stale");
    assertThat(courseMapper.updateById(writerB)).isZero();

    Course reloaded = courseMapper.selectById(id);
    assertThat(reloaded.getCourseName()).isEqualTo("A-first");
    assertThat(reloaded.getVersion()).isEqualTo(1);
  }

  @Test
  @DisplayName("updateByIdWithVersionCheck：过期版本命中 0 行时抛 HTTP 409 / code 2040")
  void updateByIdWithVersionCheck_onStale_throws409() {
    Long orgId = CourseItFixtures.defaultOrganizationId(organizationMapper);
    Course course = CourseItFixtures.newCourse(orgId, CourseItFixtures.uniqueCourseCode("IT-409-"));
    courseMapper.insert(course);
    Long id = course.getCourseId();

    Course writerA = courseMapper.selectById(id);
    writerA.setCourseName("A-win");
    courseMapper.updateByIdWithVersionCheck(writerA);

    Course writerB = staleCopy(id, "B-lose");
    assertThatThrownBy(() -> courseMapper.updateByIdWithVersionCheck(writerB))
      .isInstanceOf(BusinessException.class)
      .satisfies(ex -> {
        BusinessException be = (BusinessException) ex;
        assertThat(be.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(be.getCode()).isEqualTo(2040);
      });
  }

  private static Course staleCopy(Long courseId, String courseName) {
    Course course = new Course();
    course.setCourseId(courseId);
    course.setCourseName(courseName);
    course.setVersion(0);
    return course;
  }
}

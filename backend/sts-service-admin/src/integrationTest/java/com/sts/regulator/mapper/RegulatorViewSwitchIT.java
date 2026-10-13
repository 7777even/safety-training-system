package com.sts.regulator.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sts.admin.support.AbstractMysqlIntegrationTest;
import com.sts.admin.tenant.TenantContext;
import com.sts.course.entity.Course;
import com.sts.course.mapper.CourseMapper;
import com.sts.regulator.support.RegulatorMapperItConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 切换视角前后的可见性变化（design §4.2）。
 * <p>
 * 切换视角的设计要点是「业务 Service 与 SQL 完全不变」——监管方看到什么，
 * 完全取决于 {@link TenantContext} 绑定到哪个租户。本用例用未经改动的业务查询验证这一点：
 * 同一条 SQL，绑定监管租户时看不到企业数据，绑定目标企业时看到且只看到该企业的数据。
 */
@SpringBootTest(classes = RegulatorMapperItConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class RegulatorViewSwitchIT extends AbstractMysqlIntegrationTest {

  @Autowired
  private CourseMapper courseMapper;
  @Autowired
  private JdbcTemplate jdbcTemplate;

  private Long createTenant(String code, String orgType) {
    jdbcTemplate.update(
      "INSERT INTO sts_org_organization (org_code, org_name, org_type, status, description)"
        + " VALUES (?, ?, ?, 1, '')", code, code, orgType);
    return jdbcTemplate.queryForObject(
      "SELECT organization_id FROM sts_org_organization WHERE org_code = ?", Long.class, code);
  }

  private Long insertCourse(String code) {
    Course course = new Course();
    course.setCourseCode(code);
    course.setCourseName("课程-" + code);
    course.setContentMode("SINGLE");
    course.setCredit(BigDecimal.ONE);
    course.setPointMode("NONE");
    course.setPointValue(BigDecimal.ZERO);
    course.setSourceDownloadMode("NONE");
    course.setIsSpeedPlayAllowed(0);
    course.setIsStudyAnticheatEnabled(0);
    course.setIsStudyDescriptionEnabled(0);
    course.setIsWatermarkEnabled(0);
    course.setPublishStatus("PUBLISHED");
    course.setStatus(1);
    courseMapper.insert(course);
    return course.getCourseId();
  }

  private List<Course> listCourses() {
    return courseMapper.selectList(new LambdaQueryWrapper<Course>()
                                     .likeRight(Course::getCourseCode, "SW-"));
  }

  @Test
  @DisplayName("绑定监管租户时看不到企业课程；切到目标企业后看到且只看到该企业的课程")
  void switchingView_changesVisibilityWithoutTouchingBusinessSql() {
    Long regulator = createTenant("SW-REG", "REGULATOR");
    Long enterpriseA = createTenant("SW-A", "ENTERPRISE");
    Long enterpriseB = createTenant("SW-B", "ENTERPRISE");
    Long courseA = TenantContext.callWithTenant(enterpriseA, () -> insertCourse("SW-A-C1"));
    Long courseB = TenantContext.callWithTenant(enterpriseB, () -> insertCourse("SW-B-C1"));

    // 切换前：监管租户自身没有业务数据
    assertThat(TenantContext.callWithTenant(regulator, this::listCourses))
      .describedAs("监管租户不承载业务，未切换视角时应当看不到任何企业课程").isEmpty();

    // 切换到 A：只看到 A 的课程
    assertThat(TenantContext.callWithTenant(enterpriseA, this::listCourses))
      .extracting(Course::getCourseId).containsExactly(courseA);

    // 切换到 B：只看到 B 的课程
    assertThat(TenantContext.callWithTenant(enterpriseB, this::listCourses))
      .extracting(Course::getCourseId).containsExactly(courseB);
  }

  @Test
  @DisplayName("切换视角后按 ID 直查也只在目标租户内成立")
  void switchedView_scopesSelectByIdToTheTargetTenant() {
    Long enterpriseA = createTenant("SW-ID-A", "ENTERPRISE");
    Long enterpriseB = createTenant("SW-ID-B", "ENTERPRISE");
    Long courseA = TenantContext.callWithTenant(enterpriseA, () -> insertCourse("SW-ID-A-C1"));

    assertThat(TenantContext.callWithTenant(enterpriseA, () -> courseMapper.selectById(courseA)))
      .isNotNull();
    assertThat(TenantContext.callWithTenant(enterpriseB, () -> courseMapper.selectById(courseA)))
      .describedAs("切到 B 之后不得再读到 A 的课程").isNull();
  }

  @Test
  @DisplayName("视角切换是请求级的：退出后恢复原有租户上下文，不残留")
  void switchedView_isRestoredAfterTheRequest() {
    Long enterpriseA = createTenant("SW-R-A", "ENTERPRISE");
    Long demo = demoTenantId();

    TenantContext.runWithTenant(enterpriseA, () -> insertCourse("SW-R-A-C1"));

    assertThat(TenantContext.currentTenantIdOrNull())
      .describedAs("切换结束后 MUST 恢复原上下文，否则线程复用会串租户")
      .isEqualTo(demo);
    assertThat(listCourses())
      .describedAs("恢复后看到的应是 DEMO 的数据（此处为空）").isEmpty();
  }
}

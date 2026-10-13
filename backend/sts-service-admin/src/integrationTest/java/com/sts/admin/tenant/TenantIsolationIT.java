package com.sts.admin.tenant;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sts.admin.support.AbstractMysqlIntegrationTest;
import com.sts.course.entity.Course;
import com.sts.course.entity.CourseChapter;
import com.sts.course.mapper.CourseChapterMapper;
import com.sts.course.mapper.CourseMapper;
import com.sts.course.support.CourseMapperItConfig;
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
 * 租户隔离端到端验证（Testcontainers 真 MySQL）。
 * <p>
 * 这是本次多租户改造最关键的一组证据：只有真库执行的 SQL 才能证明租户条件确实被注入，
 * 且分页 count、子表直查、写操作都受同一边界约束。
 */
@SpringBootTest(classes = CourseMapperItConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class TenantIsolationIT extends AbstractMysqlIntegrationTest {

  @Autowired
  private CourseMapper courseMapper;

  @Autowired
  private CourseChapterMapper chapterMapper;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  private Long otherTenantId() {
    jdbcTemplate.update(
      "INSERT INTO sts_org_organization (org_code, org_name, org_type, status, description)"
        + " VALUES ('ISO-OTHER', '另一家企业', 'ENTERPRISE', 1, '')");
    return jdbcTemplate.queryForObject(
      "SELECT organization_id FROM sts_org_organization WHERE org_code = 'ISO-OTHER'", Long.class);
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
    course.setPublishStatus("DRAFT");
    course.setStatus(1);
    courseMapper.insert(course);
    return course.getCourseId();
  }

  @Test
  @DisplayName("INSERT 自动填充当前租户，无需业务代码传入")
  void insert_fillsTenantColumnAutomatically() {
    Long courseId = insertCourse("ISO-INS-1");

    Long stored = jdbcTemplate.queryForObject(
      "SELECT organization_id FROM sts_course_course WHERE course_id = ?", Long.class, courseId);
    assertThat(stored).isEqualTo(demoTenantId());
  }

  @Test
  @DisplayName("SELECT 只返回当前租户数据，其它租户不可见")
  void select_returnsOnlyCurrentTenantRows() {
    insertCourse("ISO-SEL-MINE");
    Long other = otherTenantId();
    TenantContext.callWithTenant(other, () -> insertCourse("ISO-SEL-THEIRS"));

    List<Course> mine = courseMapper.selectList(new LambdaQueryWrapper<Course>()
                                                  .likeRight(Course::getCourseCode, "ISO-SEL-"));

    assertThat(mine).extracting(Course::getCourseCode).containsExactly("ISO-SEL-MINE");
  }

  @Test
  @DisplayName("分页 count 同样受租户约束，总数不跨租户泄漏")
  void selectPage_countIsTenantScoped() {
    insertCourse("ISO-PAGE-1");
    insertCourse("ISO-PAGE-2");
    Long other = otherTenantId();
    TenantContext.callWithTenant(other, () -> {
      insertCourse("ISO-PAGE-3");
      return insertCourse("ISO-PAGE-4");
    });

    Page<Course> page = courseMapper.selectPage(
      new Page<>(1, 10),
      new LambdaQueryWrapper<Course>().likeRight(Course::getCourseCode, "ISO-PAGE-"));

    assertThat(page.getTotal()).describedAs("count 语句必须带租户条件").isEqualTo(2);
    assertThat(page.getRecords()).hasSize(2);
  }

  @Test
  @DisplayName("跨租户按主键直查返回空，ID 猜测无法越权")
  void selectById_acrossTenant_returnsNothing() {
    Long other = otherTenantId();
    Long theirCourseId = TenantContext.callWithTenant(other, () -> insertCourse("ISO-BYID"));

    assertThat(courseMapper.selectById(theirCourseId)).isNull();
  }

  @Test
  @DisplayName("跨租户按主键更新影响 0 行，数据未被改动")
  void updateById_acrossTenant_affectsNoRow() {
    Long other = otherTenantId();
    Long theirCourseId = TenantContext.callWithTenant(other, () -> insertCourse("ISO-UPD"));

    Course patch = new Course();
    patch.setCourseId(theirCourseId);
    patch.setCourseName("被篡改的名字");
    assertThat(courseMapper.updateById(patch)).isZero();

    String name = jdbcTemplate.queryForObject(
      "SELECT course_name FROM sts_course_course WHERE course_id = ?", String.class, theirCourseId);
    assertThat(name).isEqualTo("课程-ISO-UPD");
  }

  @Test
  @DisplayName("跨租户按主键删除影响 0 行，数据仍在")
  void deleteById_acrossTenant_affectsNoRow() {
    Long other = otherTenantId();
    Long theirCourseId = TenantContext.callWithTenant(other, () -> insertCourse("ISO-DEL"));

    assertThat(courseMapper.deleteById(theirCourseId)).isZero();

    Long alive = jdbcTemplate.queryForObject(
      "SELECT COUNT(*) FROM sts_course_course WHERE course_id = ? AND deleted = 0",
      Long.class, theirCourseId);
    assertThat(alive).isEqualTo(1L);
  }

  @Test
  @DisplayName("子表同样带租户列：跨租户按子表主键直查无结果")
  void childTable_isIsolatedToo() {
    Long other = otherTenantId();
    Long theirChapterId = TenantContext.callWithTenant(other, () -> {
      Long courseId = insertCourse("ISO-CHILD");
      CourseChapter chapter = new CourseChapter();
      chapter.setCourseId(courseId);
      chapter.setParentId(0L);
      chapter.setAncestors("0");
      chapter.setChapterName("他家的章节");
      chapter.setSortOrder(1);
      chapter.setStatus(1);
      chapterMapper.insert(chapter);
      return chapter.getChapterId();
    });

    assertThat(chapterMapper.selectById(theirChapterId))
      .describedAs("子表带租户列后，按子表 ID 直查不再是越权通道").isNull();
  }

  @Test
  @DisplayName("子表租户值与主表一致，插件按上下文填充不产生孤儿数据")
  void childTable_tenantMatchesParent() {
    Long courseId = insertCourse("ISO-CONSIST");
    CourseChapter chapter = new CourseChapter();
    chapter.setCourseId(courseId);
    chapter.setParentId(0L);
    chapter.setAncestors("0");
    chapter.setChapterName("本家的章节");
    chapter.setSortOrder(1);
    chapter.setStatus(1);
    chapterMapper.insert(chapter);

    Long parentTenant = jdbcTemplate.queryForObject(
      "SELECT organization_id FROM sts_course_course WHERE course_id = ?", Long.class, courseId);
    Long childTenant = jdbcTemplate.queryForObject(
      "SELECT organization_id FROM sts_course_chapter WHERE chapter_id = ?",
      Long.class, chapter.getChapterId());

    assertThat(childTenant).isEqualTo(parentTenant);
  }

  @Test
  @DisplayName("已登记的平台服务可在最小范围显式跨租户读取")
  void explicitPlatformBypass_readsAcrossTenants() {
    insertCourse("ISO-BYPASS-MINE");
    Long other = otherTenantId();
    TenantContext.callWithTenant(other, () -> insertCourse("ISO-BYPASS-THEIRS"));

    List<Course> all = TenantContext.callWithBypass(
      () -> courseMapper.selectList(new LambdaQueryWrapper<Course>()
                                      .likeRight(Course::getCourseCode, "ISO-BYPASS-")));

    assertThat(all).extracting(Course::getCourseCode)
      .containsExactlyInAnyOrder("ISO-BYPASS-MINE", "ISO-BYPASS-THEIRS");
  }
}

package com.sts.regulator.mapper;

import com.sts.admin.support.AbstractMysqlIntegrationTest;
import com.sts.admin.tenant.TenantContext;
import com.sts.course.entity.Course;
import com.sts.course.mapper.CourseMapper;
import com.sts.regulator.support.RegulatorMapperItConfig;
import com.sts.regulator.vo.LearningStatsVO;
import com.sts.regulator.vo.TenantAggregateItemVO;
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
 * 跨租户只读聚合通道（design §4.3）。
 * <p>
 * 这是全系统唯一允许无租户条件读取业务表的地方，因此必须在真库上确认两件事：
 * SQL 在跳过租户插件后语法成立并按 {@code organization_id} 正确分组；
 * 结果确实横跨多个租户，而不是被当前上下文悄悄裁剪成一个租户。
 */
@SpringBootTest(classes = RegulatorMapperItConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class RegulatorAggregateMapperIT extends AbstractMysqlIntegrationTest {

  @Autowired
  private RegulatorAggregateMapper aggregateMapper;
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

  private void insertPublishedCourse(String code) {
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
  }

  private static TenantAggregateItemVO rowOf(List<TenantAggregateItemVO> rows, Long tenantId) {
    return rows.stream().filter(r -> tenantId.equals(r.getOrganizationId())).findFirst().orElse(null);
  }

  @Test
  @DisplayName("聚合结果横跨多个企业租户，且各租户计数互不串扰")
  void aggregates_spanMultipleTenantsWithIndependentCounts() {
    Long tenantA = createTenant("AGG-A", "ENTERPRISE");
    Long tenantB = createTenant("AGG-B", "ENTERPRISE");
    TenantContext.runWithTenant(tenantA, () -> insertPublishedCourse("AGG-A-C1"));
    TenantContext.runWithTenant(tenantA, () -> insertPublishedCourse("AGG-A-C2"));
    TenantContext.runWithTenant(tenantB, () -> insertPublishedCourse("AGG-B-C1"));

    // 当前上下文仍是 DEMO：若插件未被跳过，这里只会看到 DEMO 一行
    List<TenantAggregateItemVO> rows = aggregateMapper.selectTenantAggregates();

    assertThat(rowOf(rows, tenantA)).isNotNull();
    assertThat(rowOf(rows, tenantA).getPublishedCourseCount()).isEqualTo(2);
    assertThat(rowOf(rows, tenantB).getPublishedCourseCount()).isEqualTo(1);
    assertThat(rows).extracting(TenantAggregateItemVO::getOrganizationId)
      .describedAs("聚合必须跨租户返回多行，否则 @InterceptorIgnore 没有生效")
      .contains(tenantA, tenantB);
  }

  @Test
  @DisplayName("零数据的企业租户也出现在聚合结果里，计数为 0 而非缺行")
  void tenantsWithoutData_appearWithZeroCounts() {
    Long empty = createTenant("AGG-EMPTY", "ENTERPRISE");

    TenantAggregateItemVO row = rowOf(aggregateMapper.selectTenantAggregates(), empty);

    assertThat(row).isNotNull();
    assertThat(row.getOrgCode()).isEqualTo("AGG-EMPTY");
    assertThat(row.getPublishedCourseCount()).isZero();
    assertThat(row.getRunningPlanCount()).isZero();
    assertThat(row.getParticipantCount()).isZero();
    assertThat(row.getCompletedParticipantCount()).isZero();
  }

  @Test
  @DisplayName("培训机构出现在聚合结果，监管租户自身不出现")
  void trainingInstitution_isIncludedAndRegulatorExcluded() {
    Long institution = createTenant("AGG-TI", "TRAINING_INSTITUTION");
    Long regulator = createTenant("AGG-REG-2", "REGULATOR");

    assertThat(rowOf(aggregateMapper.selectTenantAggregates(), institution)).isNotNull();
    assertThat(rowOf(aggregateMapper.selectTenantAggregates(), regulator)).isNull();
  }

  @Test
  @DisplayName("监管租户自身不出现在聚合结果里：只聚合企业租户")
  void regulatorTenantItself_isExcluded() {
    Long regulator = createTenant("AGG-REG", "REGULATOR");

    assertThat(rowOf(aggregateMapper.selectTenantAggregates(), regulator)).isNull();
  }

  @Test
  @DisplayName("停用的企业租户不出现在聚合结果里")
  void disabledTenant_isExcluded() {
    Long disabled = createTenant("AGG-OFF", "ENTERPRISE");
    jdbcTemplate.update("UPDATE sts_org_organization SET status = 0 WHERE organization_id = ?", disabled);

    assertThat(rowOf(aggregateMapper.selectTenantAggregates(), disabled)).isNull();
  }

  @Test
  @DisplayName("学习统计：无报名机构零分母为 0；有报名机构按课程报名去重")
  void learningStats_zeroDenominatorAndEnrollmentCounts() {
    Long empty = createTenant("LS-EMPTY", "ENTERPRISE");
    Long withData = createTenant("LS-DATA", "ENTERPRISE");
    TenantContext.runWithTenant(withData, () -> insertPublishedCourse("LS-C1"));
    Long courseId = jdbcTemplate.queryForObject(
      "SELECT course_id FROM sts_course_course WHERE course_code = ?", Long.class, "LS-C1");
    jdbcTemplate.update(
      "INSERT INTO us_sys_user (organization_id, user_code, user_name, member_type, status) VALUES (?,?,?,?,1)",
      withData, "ls-u1", "学员甲", "INTERNAL");
    Long userId = jdbcTemplate.queryForObject(
      "SELECT user_id FROM us_sys_user WHERE organization_id = ? AND user_code = ?",
      Long.class, withData, "ls-u1");
    jdbcTemplate.update(
      "INSERT INTO sts_course_offering (organization_id, offering_code, course_id, offering_name, course_type,"
        + " enrollment_type, eligibility_type, completion_rule, publish_status, status)"
        + " VALUES (?, 'LS-OFF', ?, '开课', 'REQUIRED', 'ASSIGNED', 'ALL', 'ALL_REQUIRED_ITEMS', 'PUBLISHED', 1)",
      withData, courseId);
    Long offeringId = jdbcTemplate.queryForObject(
      "SELECT course_offering_id FROM sts_course_offering WHERE offering_code = 'LS-OFF' AND organization_id = ?",
      Long.class, withData);
    jdbcTemplate.update(
      "INSERT INTO sts_course_enrollment (organization_id, course_offering_id, user_id, enrollment_source,"
        + " enrolled_time, learning_status, progress_percent, total_study_seconds, credit_awarded)"
        + " VALUES (?, ?, ?, 'ASSIGN', NOW(), 'LEARNING', 0, 0, 0)",
      withData, offeringId, userId);

    LearningStatsVO emptyStats = aggregateMapper.selectLearningStats(empty);
    assertThat(emptyStats.getEmployeeAccountCount()).isZero();
    assertThat(emptyStats.getClassSessionCount()).isZero();
    assertThat(emptyStats.getClassLearnerCount()).isZero();
    assertThat(emptyStats.getParticipatingOfferingCount()).isZero();
    assertThat(emptyStats.getAvgSessionsPerLearner()).isEqualByComparingTo("0");
    assertThat(emptyStats.getParticipationRate()).isEqualByComparingTo("0");

    LearningStatsVO dataStats = aggregateMapper.selectLearningStats(withData);
    assertThat(dataStats.getEmployeeAccountCount()).isEqualTo(1);
    assertThat(dataStats.getClassSessionCount()).isEqualTo(1);
    assertThat(dataStats.getClassLearnerCount()).isEqualTo(1);
    assertThat(dataStats.getParticipatingOfferingCount()).isEqualTo(1);
    assertThat(dataStats.getAvgSessionsPerLearner()).isEqualByComparingTo("1");
    assertThat(dataStats.getParticipationRate()).isEqualByComparingTo("1");
  }
}

package com.sts.training.mapper;

import com.sts.admin.support.AbstractMysqlIntegrationTest;
import com.sts.org.mapper.OrganizationMapper;
import com.sts.training.support.TrainingMapperItConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = TrainingMapperItConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class TrainingTypeSeedIT extends AbstractMysqlIntegrationTest {

  @Autowired
  private OrganizationMapper organizationMapper;
  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Test
  @DisplayName("空库 init 为 DEMO 企业写入不少于 25 种稳定编码正式培训类型")
  void initSeedsAtLeast25FormalTypesForDemo() {
    Long orgId = organizationMapper.selectByOrgCode("DEMO").getOrganizationId();
    Integer count = jdbcTemplate.queryForObject(
      "SELECT COUNT(*) FROM sts_training_type WHERE organization_id = ? AND deleted = 0 AND type_code LIKE 'SO_%'",
      Integer.class, orgId);
    assertThat(count).isGreaterThanOrEqualTo(25);
    Integer distinct = jdbcTemplate.queryForObject(
      "SELECT COUNT(DISTINCT type_code) FROM sts_training_type WHERE organization_id = ? AND deleted = 0 AND type_code LIKE 'SO_%'",
      Integer.class,
      orgId);
    assertThat(distinct).isEqualTo(count);
  }

  @Test
  @DisplayName("正式类型 seed 重复执行保持幂等")
  void seedInsertIsIdempotent() {
    Long orgId = organizationMapper.selectByOrgCode("DEMO").getOrganizationId();
    Integer before = jdbcTemplate.queryForObject(
      "SELECT COUNT(*) FROM sts_training_type WHERE organization_id = ? AND type_code = 'SO_ELEC_HV' AND deleted = 0",
      Integer.class, orgId);
    jdbcTemplate.update("""
                          INSERT INTO sts_training_type
                          (organization_id, type_code, type_name, work_type, tag, initial_hours, review_hours, online_max_ratio, status, description)
                          SELECT ?, 'SO_ELEC_HV', '高压电工作业', '电工作业', '特种作业', 80.00, 24.00, 0.3000, 1, '特种作业人员培训类型：高压电工作业'
                          FROM DUAL
                          WHERE NOT EXISTS (
                            SELECT 1 FROM sts_training_type t
                            WHERE t.organization_id = ? AND t.type_code = 'SO_ELEC_HV' AND t.deleted = 0
                          )
                          """, orgId, orgId);
    Integer after = jdbcTemplate.queryForObject(
      "SELECT COUNT(*) FROM sts_training_type WHERE organization_id = ? AND type_code = 'SO_ELEC_HV' AND deleted = 0",
      Integer.class, orgId);
    assertThat(before).isEqualTo(1);
    assertThat(after).isEqualTo(1);
  }
}

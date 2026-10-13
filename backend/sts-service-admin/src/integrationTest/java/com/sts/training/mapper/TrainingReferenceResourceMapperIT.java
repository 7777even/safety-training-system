package com.sts.training.mapper;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sts.admin.support.AbstractMysqlIntegrationTest;
import com.sts.admin.tenant.TenantContext;
import com.sts.training.support.TrainingMapperItConfig;
import com.sts.training.vo.TrainingReferenceResourceVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = TrainingMapperItConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class TrainingReferenceResourceMapperIT extends AbstractMysqlIntegrationTest {

  @Autowired
  private TrainingReferenceResourceMapper mapper;
  @Autowired
  private JdbcTemplate jdbc;

  @Test
  void coursePage_returnsOnlyPublishedEnabledResourcesFromCurrentTenant() {
    long currentOrg = organization("IT-REF-A");
    long otherOrg = organization("IT-REF-B");
    course(currentOrg, "C-AVAILABLE", "安全课程", "PUBLISHED", 1, 101L);
    course(currentOrg, "C-DRAFT", "安全草稿", "DRAFT", 1, null);
    course(otherOrg, "C-OTHER", "安全外部", "PUBLISHED", 1, 102L);

    // 机构范围完全由插件注入：SQL 里已无手写租户条件，另一租户的 C-OTHER 只能靠隔离挡住
    var page = TenantContext.callWithTenant(currentOrg, () -> mapper.selectReferenceResourcePage(
      new Page<TrainingReferenceResourceVO>(1, 10), "COURSE", "安全"));

    assertThat(page.getTotal()).isEqualTo(1);
    assertThat(page.getRecords()).extracting(TrainingReferenceResourceVO::getResourceCode)
      .containsExactly("C-AVAILABLE");
    assertThat(page.getRecords().getFirst().getVersionId()).isEqualTo(101L);
  }

  @Test
  void examAndQuestionBankPages_applyTypeSpecificAvailabilityRules() {
    long org = organization("IT-REF-C");
    exam(org, "E-AVAILABLE", "正式考试", "ONLINE", "PUBLISHED", 1, 201L);
    exam(org, "E-BANK", "题库练习考试", "BANK_PRACTICE", "PUBLISHED", 1, null);
    questionBank(org, "B-AVAILABLE", "可用题库", "ACTIVE", 1);
    questionBank(org, "B-ARCHIVED", "归档题库", "ARCHIVED", 1);

    var examPage = TenantContext.callWithTenant(org, () -> mapper.selectReferenceResourcePage(
      new Page<TrainingReferenceResourceVO>(1, 10), "EXAM", null));
    var bankPage = TenantContext.callWithTenant(org, () -> mapper.selectReferenceResourcePage(
      new Page<TrainingReferenceResourceVO>(1, 10), "QUESTION_BANK", null));

    assertThat(examPage.getRecords()).extracting(TrainingReferenceResourceVO::getResourceCode)
      .containsExactly("E-AVAILABLE");
    assertThat(examPage.getRecords().getFirst().getVersionId()).isEqualTo(201L);
    assertThat(bankPage.getRecords()).extracting(TrainingReferenceResourceVO::getResourceCode)
      .containsExactly("B-AVAILABLE");
    assertThat(bankPage.getRecords().getFirst().getVersionId()).isNull();
  }

  @Test
  void examPage_returnsStandaloneExamsAndExcludesTrainingGeneratedExams() {
    long org = organization("IT-REF-D");
    exam(org, "E-STANDALONE", "独立正式考试", "ONLINE", "PUBLISHED", 1, 201L, null);
    exam(org, "E-TRAINING-EXAM", "培训生成正式考试", "ONLINE", "PUBLISHED", 1, 202L, 9001L);
    exam(org, "E-TRAINING-BANK", "培训生成题库练习", "BANK_PRACTICE", "PUBLISHED", 1, null, 9002L);

    var examPage = TenantContext.callWithTenant(org, () -> mapper.selectReferenceResourcePage(
      new Page<TrainingReferenceResourceVO>(1, 10), "EXAM", null));

    assertThat(examPage.getRecords()).extracting(TrainingReferenceResourceVO::getResourceCode)
      .containsExactly("E-STANDALONE");
    assertThat(examPage.getRecords()).extracting(TrainingReferenceResourceVO::getResourceCode)
      .doesNotContain("E-TRAINING-EXAM", "E-TRAINING-BANK");
    assertThat(examPage.getRecords().getFirst().getVersionId()).isEqualTo(201L);
  }

  private long organization(String code) {
    jdbc.update(
      "INSERT INTO sts_org_organization (org_code, org_name, org_type, status, description) VALUES (?, ?, 'ENTERPRISE', 1, 'IT')",
      code,
      code);
    return jdbc.queryForObject("SELECT organization_id FROM sts_org_organization WHERE org_code = ?", Long.class, code);
  }

  private void course(long orgId, String code, String name, String publishStatus, int status, Long versionId) {
    jdbc.update("""
                  INSERT INTO sts_course_course
                  (organization_id, course_code, course_name, current_version_id, content_mode, credit, point_mode,
                   point_value, source_download_mode, is_speed_play_allowed, is_study_anticheat_enabled,
                   is_study_description_enabled, is_watermark_enabled, publish_status, status)
                  VALUES (?, ?, ?, ?, 'SINGLE', 1, 'NONE', 0, 'NONE', 0, 0, 0, 0, ?, ?)
                  """, orgId, code, name, versionId, publishStatus, status);
  }

  private void exam(long orgId, String code, String name, String type, String publishStatus,
                    int status, Long paperVersionId) {
    exam(orgId, code, name, type, publishStatus, status, paperVersionId, null);
  }

  private void exam(long orgId, String code, String name, String type, String publishStatus,
                    int status, Long paperVersionId, Long planContentId) {
    jdbc.update("""
                  INSERT INTO sts_exam_exam
                  (organization_id, exam_code, exam_name, exam_type, paper_version_id, plan_content_id,
                   duration_seconds, pass_ratio, pass_score, credit, question_order_mode, multi_select_scoring,
                   is_notice_enabled, publish_status, status)
                  VALUES (?, ?, ?, ?, ?, ?, 3600, 0.6, 60, 1, 'TYPE', 'ALL_OR_NOTHING', 0, ?, ?)
                  """, orgId, code, name, type, paperVersionId, planContentId, publishStatus, status);
  }

  private void questionBank(long orgId, String code, String name, String archiveStatus, int status) {
    jdbc.update("""
                  INSERT INTO sts_question_bank
                  (organization_id, bank_code, bank_name, difficulty, question_order_mode, archive_status, status)
                  VALUES (?, ?, ?, 'NORMAL', 'TYPE', ?, ?)
                  """, orgId, code, name, archiveStatus, status);
  }
}

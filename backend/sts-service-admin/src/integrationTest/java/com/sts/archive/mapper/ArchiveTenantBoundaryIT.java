package com.sts.archive.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sts.admin.support.AbstractMysqlIntegrationTest;
import com.sts.admin.tenant.TenantContext;
import com.sts.archive.entity.ArchiveExportJob;
import com.sts.archive.entity.ArchiveTraining;
import com.sts.archive.support.ArchiveMapperItConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 培训档案与导出任务的租户边界（Testcontainers 真 MySQL）。
 * <p>
 * 档案是"汇集事实"的产物，导出任务会把档案落成文件带出系统，
 * 两者都必须只看得到本租户的行。
 */
@SpringBootTest(classes = ArchiveMapperItConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class ArchiveTenantBoundaryIT extends AbstractMysqlIntegrationTest {

  @Autowired
  private ArchiveTrainingMapper archiveMapper;
  @Autowired
  private ArchiveExportJobMapper exportJobMapper;
  @Autowired
  private JdbcTemplate jdbcTemplate;

  private Long otherTenantId() {
    jdbcTemplate.update(
      "INSERT INTO sts_org_organization (org_code, org_name, org_type, status, description)"
        + " VALUES ('AR-OTHER', '另一家企业', 'ENTERPRISE', 1, '')");
    return jdbcTemplate.queryForObject(
      "SELECT organization_id FROM sts_org_organization WHERE org_code = 'AR-OTHER'", Long.class);
  }

  private Long insertArchive(String code, Long planId) {
    ArchiveTraining archive = new ArchiveTraining();
    archive.setArchiveCode(code);
    archive.setTrainingPlanId(planId);
    archive.setCurrentVersionNo(1);
    archive.setArchiveStatus("DRAFT");
    archive.setStatus(1);
    archiveMapper.insert(archive);
    return archive.getTrainingArchiveId();
  }

  private Long insertExportJob(Long scopeId) {
    ArchiveExportJob job = new ArchiveExportJob();
    job.setExportScope("ARCHIVE");
    job.setScopeId(scopeId);
    job.setExportFormat("PDF");
    job.setJobStatus("PENDING");
    job.setRequestedBy(6001L);
    exportJobMapper.insert(job);
    return job.getArchiveExportJobId();
  }

  @Test
  @DisplayName("跨租户按 ID 直查培训档案与导出任务一律返回空")
  void selectById_isRejectedAcrossTenants() {
    Long other = otherTenantId();
    Long archiveId = TenantContext.callWithTenant(other, () -> insertArchive("AR-CROSS", 6101L));
    Long jobId = TenantContext.callWithTenant(other, () -> insertExportJob(archiveId));

    assertThat(archiveMapper.selectById(archiveId)).isNull();
    assertThat(exportJobMapper.selectById(jobId)).isNull();
  }

  /**
   * 导出任务列表此前按 {@code @RequestParam organizationId} 过滤，客户端可指定任意机构。
   * 参数删除后条件全部来自插件，这里固化"列表只含本租户任务"。
   */
  @Test
  @DisplayName("导出任务列表只返回本租户任务，且 count 与列表一致")
  void listExportJobs_onlyReturnsOwnTenantJobs() {
    Long other = otherTenantId();
    Long ownArchiveId = insertArchive("AR-OWN", 6102L);
    insertExportJob(ownArchiveId);
    Long otherArchiveId = TenantContext.callWithTenant(other, () -> insertArchive("AR-HIDDEN", 6103L));
    TenantContext.runWithTenant(other, () -> insertExportJob(otherArchiveId));

    var jobs = exportJobMapper.selectList(new LambdaQueryWrapper<ArchiveExportJob>());

    assertThat(jobs).hasSize(1);
    assertThat(jobs.getFirst().getOrganizationId()).isEqualTo(demoTenantId());
    assertThat(exportJobMapper.selectCount(new LambdaQueryWrapper<ArchiveExportJob>()))
      .describedAs("count 必须与列表一致").isEqualTo(1L);
  }

  @Test
  @DisplayName("跨租户封存档案影响 0 行")
  void updateById_cannotSealOtherTenantArchive() {
    Long other = otherTenantId();
    Long archiveId = TenantContext.callWithTenant(other, () -> insertArchive("AR-UPD", 6104L));

    ArchiveTraining tampered = new ArchiveTraining();
    tampered.setTrainingArchiveId(archiveId);
    tampered.setArchiveStatus("SEALED");
    assertThat(archiveMapper.updateById(tampered)).isZero();

    String stored = jdbcTemplate.queryForObject(
      "SELECT archive_status FROM sts_archive_training WHERE training_archive_id = ?", String.class, archiveId);
    assertThat(stored).isEqualTo("DRAFT");
  }

  @Test
  @DisplayName("档案编码在不同租户之间可以重名")
  void archiveCode_isUniqueOnlyWithinTenant() {
    Long other = otherTenantId();
    insertArchive("AR-SAME-CODE", 6105L);

    // uk_plan_deleted 不带租户列：计划 ID 全局唯一，本就只属于一个租户，这里用不同计划
    Long otherArchiveId = TenantContext.callWithTenant(other, () -> insertArchive("AR-SAME-CODE", 6106L));

    assertThat(otherArchiveId).isNotNull();
    assertThat(archiveMapper.selectList(new LambdaQueryWrapper<ArchiveTraining>()
                                          .eq(ArchiveTraining::getArchiveCode, "AR-SAME-CODE"))).hasSize(1);
  }
}

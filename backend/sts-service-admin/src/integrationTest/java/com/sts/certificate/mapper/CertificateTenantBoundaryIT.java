package com.sts.certificate.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sts.admin.support.AbstractMysqlIntegrationTest;
import com.sts.admin.tenant.TenantContext;
import com.sts.certificate.entity.CertificateCertificate;
import com.sts.certificate.entity.CertificateIssueRule;
import com.sts.certificate.entity.CertificateTemplate;
import com.sts.certificate.support.CertificateMapperItConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 证书模板、签发规则与已签发证书的租户边界（Testcontainers 真 MySQL）。
 * <p>
 * 签发规则列表此前按 {@code @RequestParam organizationId} 过滤，是明确的越权读取通道；
 * 证书本身一旦跨租户可读可改，等同于伪造与撤销别家资质。
 */
@SpringBootTest(classes = CertificateMapperItConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class CertificateTenantBoundaryIT extends AbstractMysqlIntegrationTest {

  @Autowired
  private CertificateTemplateMapper templateMapper;
  @Autowired
  private CertificateIssueRuleMapper ruleMapper;
  @Autowired
  private CertificateCertificateMapper certificateMapper;
  @Autowired
  private JdbcTemplate jdbcTemplate;

  private Long otherTenantId() {
    jdbcTemplate.update(
      "INSERT INTO sts_org_organization (org_code, org_name, org_type, status, description)"
        + " VALUES ('CE-OTHER', '另一家企业', 'ENTERPRISE', 1, '')");
    return jdbcTemplate.queryForObject(
      "SELECT organization_id FROM sts_org_organization WHERE org_code = 'CE-OTHER'", Long.class);
  }

  private Long insertTemplate(String code) {
    CertificateTemplate template = new CertificateTemplate();
    template.setTemplateCode(code);
    template.setTemplateName("模板-" + code);
    template.setCurrentVersionNo(1);
    template.setStatus(1);
    template.setDescription("IT");
    templateMapper.insert(template);
    return template.getCertificateTemplateId();
  }

  private Long insertRule(Long templateId, Long sourceId) {
    CertificateIssueRule rule = new CertificateIssueRule();
    rule.setRuleName("规则-" + sourceId);
    rule.setCertificateTemplateId(templateId);
    rule.setSourceType("TRAINING");
    rule.setSourceId(sourceId);
    rule.setConditionType("PASS");
    rule.setConditionValue(BigDecimal.ONE);
    rule.setIsAutoIssue(1);
    rule.setStatus(1);
    ruleMapper.insert(rule);
    return rule.getCertificateRuleId();
  }

  private Long insertCertificate(Long templateId, String no, Long holderUserId) {
    CertificateCertificate cert = new CertificateCertificate();
    cert.setCertificateNo(no);
    cert.setCertificateTemplateId(templateId);
    cert.setTemplateVersionId(1L);
    cert.setHolderUserId(holderUserId);
    cert.setSourceType("TRAINING");
    cert.setSourceId(5001L);
    cert.setIssuedTime(LocalDateTime.now());
    cert.setCertificateFileId(1L);
    cert.setContentSnapshotJson("{\"holder\":\"张三\"}");
    cert.setSnapshotHash("hash-" + no);
    cert.setCertificateStatus("ISSUED");
    certificateMapper.insert(cert);
    return cert.getCertificateId();
  }

  @Test
  @DisplayName("跨租户按 ID 直查证书模板、签发规则、证书一律返回空")
  void selectById_isRejectedAcrossTenants() {
    Long other = otherTenantId();
    Long templateId = TenantContext.callWithTenant(other, () -> insertTemplate("CE-TPL"));
    Long ruleId = TenantContext.callWithTenant(other, () -> insertRule(templateId, 5101L));
    Long certId = TenantContext.callWithTenant(other,
                                               () -> insertCertificate(templateId, "CE-NO-CROSS", 5201L));

    assertThat(templateMapper.selectById(templateId)).isNull();
    assertThat(ruleMapper.selectById(ruleId)).isNull();
    assertThat(certificateMapper.selectById(certId)).isNull();
  }

  /**
   * 对应 {@code GET /api/certificate/rules} 删除 organizationId 参数后的行为。
   */
  @Test
  @DisplayName("签发规则列表只返回本租户规则，且 count 与列表一致")
  void listRules_onlyReturnsOwnTenantRules() {
    Long other = otherTenantId();
    Long ownTemplateId = insertTemplate("CE-OWN-TPL");
    insertRule(ownTemplateId, 5102L);
    Long otherTemplateId = TenantContext.callWithTenant(other, () -> insertTemplate("CE-HIDDEN-TPL"));
    TenantContext.runWithTenant(other, () -> insertRule(otherTemplateId, 5103L));

    var rules = ruleMapper.selectList(new LambdaQueryWrapper<CertificateIssueRule>());

    assertThat(rules).hasSize(1);
    assertThat(rules.getFirst().getOrganizationId()).isEqualTo(demoTenantId());
    assertThat(ruleMapper.selectCount(new LambdaQueryWrapper<CertificateIssueRule>()))
      .describedAs("count 必须与列表一致").isEqualTo(1L);
  }

  @Test
  @DisplayName("跨租户撤销证书影响 0 行：撤销写不到别家证书")
  void updateById_cannotRevokeOtherTenantCertificate() {
    Long other = otherTenantId();
    Long templateId = TenantContext.callWithTenant(other, () -> insertTemplate("CE-UPD-TPL"));
    Long certId = TenantContext.callWithTenant(other,
                                               () -> insertCertificate(templateId, "CE-NO-UPD", 5202L));

    CertificateCertificate tampered = new CertificateCertificate();
    tampered.setCertificateId(certId);
    tampered.setCertificateStatus("REVOKED");
    assertThat(certificateMapper.updateById(tampered)).isZero();

    String stored = jdbcTemplate.queryForObject(
      "SELECT certificate_status FROM sts_certificate_certificate WHERE certificate_id = ?", String.class, certId);
    assertThat(stored).isEqualTo("ISSUED");
  }

  /**
   * 证书号的唯一键 {@code uk_certificate_no_deleted} 不含租户列，全平台唯一——
   * 这是刻意的：证书号对外出示，跨租户重号会让核验失去意义。
   * 此处固化该语义，避免后续误以为它漏加了租户列。
   */
  @Test
  @DisplayName("证书号全局唯一：跨租户不可重号")
  void certificateNo_isGloballyUniqueAcrossTenants() {
    Long other = otherTenantId();
    Long ownTemplateId = insertTemplate("CE-NO-TPL");
    insertCertificate(ownTemplateId, "CE-SHARED-NO", 5203L);

    Long otherTemplateId = TenantContext.callWithTenant(other, () -> insertTemplate("CE-NO-TPL-2"));

    assertThat(org.assertj.core.api.Assertions.catchThrowable(
      () -> TenantContext.runWithTenant(other,
                                        () -> insertCertificate(otherTemplateId, "CE-SHARED-NO", 5204L))))
      .describedAs("证书号跨租户重复必须被唯一键拒绝")
      .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
  }
}

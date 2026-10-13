package com.sts.certificate.mapper;

import com.sts.admin.support.AbstractMysqlIntegrationTest;
import com.sts.certificate.entity.CertificateCertificate;
import com.sts.certificate.support.CertificateMapperItConfig;
import com.sts.org.mapper.OrganizationMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = CertificateMapperItConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class CertificateMapperIT extends AbstractMysqlIntegrationTest {

  @Autowired
  private CertificateCertificateMapper certificateMapper;
  @Autowired
  private OrganizationMapper organizationMapper;
  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Test
  @DisplayName("uk_holder_source_deleted：幂等签发唯一")
  void holderSourceUnique() {
    Long orgId = organizationMapper.selectByOrgCode("DEMO").getOrganizationId();
    CertificateCertificate first = cert(orgId, "CERT-IT-1", 100L);
    certificateMapper.insert(first);

    CertificateCertificate dup = cert(orgId, "CERT-IT-2", 100L);
    assertThatThrownBy(() -> certificateMapper.insert(dup)).isInstanceOf(DuplicateKeyException.class);
  }

  @Test
  @DisplayName("uk_certificate_no_deleted：逻辑删除后可复用证书号")
  void certNoReuseAfterLogicDelete() {
    Long orgId = organizationMapper.selectByOrgCode("DEMO").getOrganizationId();
    CertificateCertificate first = cert(orgId, "CERT-REUSE", 200L);
    certificateMapper.insert(first);
    Long id = first.getCertificateId();
    jdbcTemplate.update("UPDATE sts_certificate_certificate SET deleted = ? WHERE certificate_id = ?", id, id);

    CertificateCertificate second = cert(orgId, "CERT-REUSE", 201L);
    certificateMapper.insert(second);
    assertThat(second.getCertificateId()).isNotNull();
  }

  private CertificateCertificate cert(Long orgId, String no, Long holder) {
    CertificateCertificate c = new CertificateCertificate();
    c.setOrganizationId(orgId);
    c.setCertificateNo(no);
    c.setCertificateTemplateId(1L);
    c.setTemplateVersionId(1L);
    c.setHolderUserId(holder);
    c.setSourceType("TRAINING");
    c.setSourceId(1L);
    c.setIssuedTime(LocalDateTime.now());
    c.setCertificateFileId(1L);
    c.setContentSnapshotJson("{}");
    c.setSnapshotHash("h");
    c.setCertificateStatus("ISSUED");
    return c;
  }
}

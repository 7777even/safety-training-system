package com.sts.admin.system;

import com.sts.admin.entity.FileMultipartUpload;
import com.sts.admin.entity.SysFileInfo;
import com.sts.admin.mapper.FileMultipartUploadMapper;
import com.sts.admin.mapper.SysFileInfoMapper;
import com.sts.admin.support.AbstractMysqlIntegrationTest;
import com.sts.admin.support.MapperItConfig;
import com.sts.admin.system.service.MultipartUploadPersistenceService;
import com.sts.admin.tenant.TenantContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = MapperItConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class MultipartUploadPersistenceTenantIT extends AbstractMysqlIntegrationTest {
  @Autowired
  private FileMultipartUploadMapper sessionMapper;
  @Autowired
  private SysFileInfoMapper fileInfoMapper;
  @Autowired
  private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void multipartTableMustComeFromInitializationSchema() {
    Integer exists = jdbcTemplate.queryForObject(
      "SELECT COUNT(*) FROM information_schema.tables"
        + " WHERE table_schema = DATABASE() AND table_name = 'sts_file_multipart_upload'",
      Integer.class);
    assertThat(exists).isEqualTo(1);
  }

  @Test
  void completionPersistsFileInLoginTenant() {
    Long organizationId = demoTenantId();
    FileMultipartUpload session = session(organizationId);
    sessionMapper.insert(session);
    SysFileInfo fileInfo = fileInfo();
    MultipartUploadPersistenceService persistence =
      new MultipartUploadPersistenceService(sessionMapper, fileInfoMapper);

    TenantContext.setTenantId(organizationId);
    Long fileInfoId = persistence.complete(session, fileInfo);

    assertThat(TenantContext.requireTenantId()).isEqualTo(organizationId);
    assertThat(jdbcTemplate.queryForObject(
      "SELECT organization_id FROM us_sys_file_info WHERE file_info_id = ?",
      Long.class, fileInfoId)).isEqualTo(organizationId);
    assertThat(jdbcTemplate.queryForObject(
      "SELECT upload_status FROM sts_file_multipart_upload WHERE multipart_upload_id = ?",
      String.class, session.getMultipartUploadId())).isEqualTo("COMPLETED");
  }

  private static FileMultipartUpload session(Long organizationId) {
    FileMultipartUpload session = new FileMultipartUpload();
    session.setUploadId("tenant-complete-it");
    session.setOrganizationId(organizationId);
    session.setUserId(7L);
    session.setProviderUploadId("provider-tenant-complete-it");
    session.setBucketName("sts-private");
    session.setObjectName("source/tenant-complete-it.mp4");
    session.setFolder("asset");
    session.setOriginalFileName("video.mp4");
    session.setContentType("video/mp4");
    session.setFileSize(3L);
    session.setChecksum("a".repeat(64));
    session.setPartSize(10L);
    session.setUploadStatus("UPLOADING");
    session.setExpiresTime(LocalDateTime.now().plusHours(1));
    return session;
  }

  private static SysFileInfo fileInfo() {
    SysFileInfo fileInfo = new SysFileInfo();
    fileInfo.setFileName("video.mp4");
    fileInfo.setBucketName("sts-private");
    fileInfo.setObjectName("source/tenant-complete-it.mp4");
    fileInfo.setFileSize(3L);
    fileInfo.setFileSuffix("mp4");
    fileInfo.setFileUrl("minio-private://source/tenant-complete-it.mp4");
    return fileInfo;
  }
}

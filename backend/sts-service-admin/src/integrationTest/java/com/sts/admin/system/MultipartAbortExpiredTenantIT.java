package com.sts.admin.system;

import com.sts.admin.mapper.FileMultipartUploadMapper;
import com.sts.admin.storage.S3StorageGateway;
import com.sts.admin.storage.StorageProperties;
import com.sts.admin.storage.TenantObjectKeyService;
import com.sts.admin.support.AbstractMysqlIntegrationTest;
import com.sts.admin.support.MapperItConfig;
import com.sts.admin.system.service.AssetWordDocumentValidator;
import com.sts.admin.system.service.MultipartUploadPersistenceService;
import com.sts.admin.system.service.MultipartUploadServiceImpl;
import com.sts.admin.tenant.CurrentTenantResolver;
import com.sts.admin.tenant.TenantContext;
import com.sts.asset.service.AssetOriginalFileSizeValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 分片会话过期清理的租户边界（Testcontainers 真 MySQL）。
 * <p>
 * 这是全平台运维任务，MUST 覆盖全部租户。此前它没有声明租户，靠
 * {@code TenantLineHandlerImpl} 在"非 Web 线程且上下文缺失"时放行才跨租户生效——
 * 一旦调用线程恰好带着某个租户上下文（例如被请求线程触发、或线程池残留），
 * 扫描就会被静默裁剪成单租户，过期会话漏清且没有任何报错。
 * <p>
 * 因此下面的用例<b>特意在绑定租户上下文的情况下</b>调用清理：
 * 显式旁路的实现能清全部租户，旧的隐式写法只能清当前租户。
 */
@SpringBootTest(classes = MapperItConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class MultipartAbortExpiredTenantIT extends AbstractMysqlIntegrationTest {

  @Autowired
  private FileMultipartUploadMapper sessionMapper;
  @Autowired
  private JdbcTemplate jdbcTemplate;

  private MultipartUploadServiceImpl service;

  @BeforeEach
  void setUp() {
    // init 完整 SQL已创建该表，只清理当前用例所需数据。
    jdbcTemplate.update("DELETE FROM sts_file_multipart_upload");
    // 清理任务只用到 sessionMapper 与 gateway，其余协作方与本用例无关
    service = new MultipartUploadServiceImpl(
      sessionMapper,
      mock(S3StorageGateway.class),
      mock(CurrentTenantResolver.class),
      mock(TenantObjectKeyService.class),
      mock(MultipartUploadPersistenceService.class),
      mock(AssetWordDocumentValidator.class),
      mock(AssetOriginalFileSizeValidator.class),
      storageProperties(10L, 24));
  }

  private static StorageProperties storageProperties(long partSize, int expireHours) {
    StorageProperties properties = new StorageProperties();
    properties.getMinio().setMultipartPartSize(partSize);
    properties.getMinio().setMultipartExpireHours(expireHours);
    return properties;
  }

  private void insertExpiredSession(String uploadId, Long organizationId) {
    jdbcTemplate.update("""
                          INSERT INTO sts_file_multipart_upload
                            (upload_id, organization_id, user_id, provider_upload_id, bucket_name, object_name,
                             folder, original_file_name, content_type, file_size, checksum, part_size,
                             upload_status, expires_time)
                          VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'UPLOADING',
                                  DATE_SUB(NOW(), INTERVAL 1 HOUR))
                          """, uploadId, organizationId, 7L, "provider-" + uploadId, "sts-private",
                        "source/" + uploadId + ".mp4", "asset", "video.mp4", "video/mp4",
                        20L, "a".repeat(64), 10L);
  }

  private String statusOf(String uploadId) {
    return jdbcTemplate.queryForObject(
      "SELECT upload_status FROM sts_file_multipart_upload WHERE upload_id = ?", String.class, uploadId);
  }

  @Test
  @DisplayName("即使调用线程绑定了某个租户，过期清理仍覆盖全部租户")
  void abortExpired_coversAllTenantsEvenWhenATenantIsBound() {
    Long demo = demoTenantId();
    Long other = demo + 9999;
    insertExpiredSession("EXP-DEMO", demo);
    insertExpiredSession("EXP-OTHER", other);

    // 基类已绑定 DEMO；旧实现在此处只会清掉 EXP-DEMO
    assertThat(TenantContext.currentTenantIdOrNull()).isEqualTo(demo);
    int cleaned = service.abortExpired();

    assertThat(cleaned).isEqualTo(2);
    assertThat(statusOf("EXP-DEMO")).isEqualTo("EXPIRED");
    assertThat(statusOf("EXP-OTHER"))
      .describedAs("旧实现依赖上下文缺失才能跨租户，绑定租户时会漏清别家会话")
      .isEqualTo("EXPIRED");
  }

  @Test
  @DisplayName("未过期会话不受影响")
  void abortExpired_leavesActiveSessionsUntouched() {
    Long demo = demoTenantId();
    insertExpiredSession("EXP-OLD", demo);
    jdbcTemplate.update("""
                          INSERT INTO sts_file_multipart_upload
                            (upload_id, organization_id, user_id, provider_upload_id, bucket_name, object_name,
                             folder, original_file_name, content_type, file_size, checksum, part_size,
                             upload_status, expires_time)
                          VALUES ('EXP-ACTIVE', ?, 7, 'provider-active', 'sts-private', 'source/active.mp4',
                                  'asset', 'video.mp4', 'video/mp4', 20, ?, 10, 'UPLOADING',
                                  DATE_ADD(NOW(), INTERVAL 24 HOUR))
                          """, demo, "b".repeat(64));

    assertThat(service.abortExpired()).isEqualTo(1);
    assertThat(statusOf("EXP-OLD")).isEqualTo("EXPIRED");
    assertThat(statusOf("EXP-ACTIVE")).isEqualTo("UPLOADING");
  }

  @Test
  @DisplayName("清理结束后不残留租户上下文，仍是调用前的租户")
  void abortExpired_restoresTheCallerContext() {
    Long demo = demoTenantId();
    insertExpiredSession("EXP-CTX", demo);

    service.abortExpired();

    assertThat(TenantContext.currentTenantIdOrNull())
      .describedAs("callWithBypass / callWithTenant 必须恢复原上下文，否则线程池复用会串租户")
      .isEqualTo(demo);
  }
}

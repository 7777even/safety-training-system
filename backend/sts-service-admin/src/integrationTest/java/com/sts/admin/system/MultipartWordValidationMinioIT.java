package com.sts.admin.system;

import com.sts.admin.entity.FileMultipartUpload;
import com.sts.admin.mapper.FileMultipartUploadMapper;
import com.sts.admin.mapper.SysFileInfoMapper;
import com.sts.admin.storage.AwsS3StorageGateway;
import com.sts.admin.storage.CompletedStoragePart;
import com.sts.admin.storage.StorageProperties;
import com.sts.admin.storage.TenantObjectKeyService;
import com.sts.admin.system.dto.MultipartCompleteRequest;
import com.sts.admin.system.service.AssetWordDocumentValidator;
import com.sts.admin.system.service.MultipartUploadPersistenceService;
import com.sts.admin.system.service.MultipartUploadServiceImpl;
import com.sts.admin.tenant.CurrentTenantResolver;
import com.sts.asset.service.AssetOriginalFileSizeValidator;
import com.sts.common.exception.BusinessException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MultipartWordValidationMinioIT {
  private static final String ACCESS_KEY = "sts-test-access";
  private static final String SECRET_KEY = "sts-test-secret-123";
  private static final GenericContainer<?> MINIO = new GenericContainer<>(
    DockerImageName.parse("minio/minio:RELEASE.2024-12-18T13-15-44Z"))
    .withEnv("MINIO_ROOT_USER", ACCESS_KEY)
    .withEnv("MINIO_ROOT_PASSWORD", SECRET_KEY)
    .withCommand("server", "/data")
    .withExposedPorts(9000);

  private static S3Client client;
  private static AwsS3StorageGateway gateway;

  @BeforeAll
  static void start() {
    MINIO.start();
    client = S3Client.builder()
      .endpointOverride(URI.create("http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000)))
      .region(Region.US_EAST_1)
      .credentialsProvider(StaticCredentialsProvider.create(
        AwsBasicCredentials.create(ACCESS_KEY, SECRET_KEY)))
      .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
      .build();
    StorageProperties.Minio properties = new StorageProperties.Minio();
    properties.setBucket("sts-word-it");
    properties.setAutoCreateBucket(true);
    gateway = new AwsS3StorageGateway(client, properties);
  }

  @AfterAll
  static void stop() {
    if (client != null) client.close();
    MINIO.stop();
  }

  @Test
  void incompatibleMultipartDocxDeletesMergedObjectAndAbortsSession() throws Exception {
    byte[] content = riskyDocx();
    String objectName = "source/it/vml.docx";
    String providerUploadId = gateway.createMultipartUpload(
      objectName, "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    String etag = gateway.uploadPart(objectName, providerUploadId, 1,
                                     new ByteArrayInputStream(content), content.length);

    FileMultipartUpload session = session(objectName, providerUploadId, content);
    FileMultipartUploadMapper sessionMapper = mock(FileMultipartUploadMapper.class);
    SysFileInfoMapper fileInfoMapper = mock(SysFileInfoMapper.class);
    CurrentTenantResolver tenantResolver = mock(CurrentTenantResolver.class);
    when(tenantResolver.resolve(7L)).thenReturn(10L);
    when(sessionMapper.selectOne(any())).thenReturn(session);
    MultipartUploadServiceImpl service = new MultipartUploadServiceImpl(
      sessionMapper, gateway, tenantResolver, mock(TenantObjectKeyService.class),
      new MultipartUploadPersistenceService(sessionMapper, fileInfoMapper),
      new AssetWordDocumentValidator(), new AssetOriginalFileSizeValidator(),
      storageProperties());

    assertThatThrownBy(() -> service.complete(
      7L, "public-it", new MultipartCompleteRequest(List.of(new CompletedStoragePart(1, etag)))))
      .isInstanceOfSatisfying(BusinessException.class,
                              error -> assertThat(error.getCode()).isEqualTo(5006));

    assertThatThrownBy(() -> gateway.getObject(objectName)).isInstanceOf(RuntimeException.class);
    verify(sessionMapper).updateById(argThat((FileMultipartUpload updated) ->
                                               "ABORTED".equals(updated.getUploadStatus())));
    verify(fileInfoMapper, never()).insert(any(com.sts.admin.entity.SysFileInfo.class));
  }

  private static StorageProperties storageProperties() {
    StorageProperties properties = new StorageProperties();
    properties.getMinio().setMultipartPartSize(10 * 1024 * 1024L);
    properties.getMinio().setMultipartExpireHours(24);
    return properties;
  }

  private static FileMultipartUpload session(String objectName, String providerUploadId, byte[] content)
    throws Exception {
    FileMultipartUpload session = new FileMultipartUpload();
    session.setMultipartUploadId(1L);
    session.setUploadId("public-it");
    session.setOrganizationId(10L);
    session.setUserId(7L);
    session.setProviderUploadId(providerUploadId);
    session.setBucketName("sts-word-it");
    session.setObjectName(objectName);
    session.setFolder("asset");
    session.setOriginalFileName("vml.docx");
    session.setContentType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    session.setFileSize((long) content.length);
    session.setChecksum(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content)));
    session.setPartSize(10 * 1024 * 1024L);
    session.setUploadStatus("UPLOADING");
    session.setExpiresTime(LocalDateTime.now().plusHours(1));
    return session;
  }

  private static byte[] riskyDocx() throws Exception {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    try (ZipOutputStream zip = new ZipOutputStream(output)) {
      write(zip, "[Content_Types].xml", """
        <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
          <Override PartName="/word/document.xml"
            ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
        </Types>
        """);
      write(zip, "_rels/.rels", """
        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
          <Relationship Id="rId1"
            Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument"
            Target="word/document.xml"/>
        </Relationships>
        """);
      write(zip, "word/document.xml", """
        <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"
                    xmlns:v="urn:schemas-microsoft-com:vml"><w:body><v:textbox/></w:body></w:document>
        """);
    }
    return output.toByteArray();
  }

  private static void write(ZipOutputStream zip, String name, String content) throws Exception {
    zip.putNextEntry(new ZipEntry(name));
    zip.write(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    zip.closeEntry();
  }
}

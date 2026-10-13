package com.sts.admin.storage;

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
import java.net.URI;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AwsS3StorageGatewayIT {
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
    properties.setBucket("sts-it");
    properties.setAutoCreateBucket(true);
    gateway = new AwsS3StorageGateway(client, properties);
  }

  @AfterAll
  static void stop() {
    if (client != null) client.close();
    MINIO.stop();
  }

  @Test
  void multipartCanResumeCompleteVerifyAndDelete() throws Exception {
    String key = "source/it/video.bin";
    byte[] first = new byte[5 * 1024 * 1024];
    byte[] second = "end".getBytes();
    String uploadId = gateway.createMultipartUpload(key, "application/octet-stream");
    String etag1 = gateway.uploadPart(key, uploadId, 1,
                                      new ByteArrayInputStream(first), first.length);

    assertThat(gateway.listParts(key, uploadId))
      .extracting(UploadedStoragePart::partNumber).containsExactly(1);

    String etag2 = gateway.uploadPart(key, uploadId, 2,
                                      new ByteArrayInputStream(second), second.length);
    gateway.completeMultipartUpload(key, uploadId,
                                    List.of(new CompletedStoragePart(1, etag1), new CompletedStoragePart(2, etag2)));

    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    digest.update(first);
    digest.update(second);
    assertThat(gateway.objectSize(key)).isEqualTo(first.length + second.length);
    assertThat(gateway.sha256(key)).isEqualTo(HexFormat.of().formatHex(digest.digest()));

    gateway.deleteObject(key);
  }

  @Test
  void multipartCanBeAborted() {
    String key = "source/it/abort.bin";
    String uploadId = gateway.createMultipartUpload(key, "application/octet-stream");
    gateway.uploadPart(key, uploadId, 1, new ByteArrayInputStream(new byte[]{1}), 1);
    gateway.abortMultipartUpload(key, uploadId);
    assertThatThrownBy(() -> gateway.listParts(key, uploadId)).isInstanceOf(RuntimeException.class);
  }

  @Test
  void ordinaryPrivateObjectCanBeUploadedReadAndDeleted() throws Exception {
    String key = "source/it/single.txt";
    byte[] content = "private-content".getBytes();
    gateway.putObject(key, new ByteArrayInputStream(content), content.length, "text/plain");

    StorageObject stored = gateway.getObject(key);
    try (var input = stored.inputStream()) {
      assertThat(stored.size()).isEqualTo(content.length);
      assertThat(stored.contentType()).isEqualTo("text/plain");
      assertThat(input.readAllBytes()).isEqualTo(content);
    }
    StorageObject ranged = gateway.getObjectRange(key, 2, 6);
    try (var input = ranged.inputStream()) {
      assertThat(ranged.size()).isEqualTo(5);
      assertThat(new String(input.readAllBytes())).isEqualTo("ivate");
    }

    gateway.deleteObject(key);
    assertThatThrownBy(() -> gateway.getObject(key)).isInstanceOf(RuntimeException.class);
  }

  @Test
  void missingBucketFailsWhenAutoCreationIsDisabled() {
    StorageProperties.Minio properties = new StorageProperties.Minio();
    properties.setBucket("missing-sts-bucket");
    properties.setAutoCreateBucket(false);
    assertThatThrownBy(() -> new AwsS3StorageGateway(client, properties))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("bucket");
  }
}

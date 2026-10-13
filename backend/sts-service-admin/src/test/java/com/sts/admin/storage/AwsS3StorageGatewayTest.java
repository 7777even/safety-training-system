package com.sts.admin.storage;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketResponse;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;
import software.amazon.awssdk.services.s3.model.UploadPartResponse;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AwsS3StorageGatewayTest {

  @Test
  void multipartRequestBodyCanBeReadAgainForSdkRetry() throws Exception {
    S3Client client = mock(S3Client.class);
    when(client.headBucket(any(HeadBucketRequest.class))).thenReturn(HeadBucketResponse.builder().build());
    byte[] content = "retryable-part".getBytes();
    when(client.uploadPart(any(UploadPartRequest.class), any(RequestBody.class)))
      .thenAnswer(invocation -> {
        RequestBody body = invocation.getArgument(1);
        try (var first = body.contentStreamProvider().newStream();
             var retry = body.contentStreamProvider().newStream()) {
          assertThat(first.readAllBytes()).isEqualTo(content);
          assertThat(retry.readAllBytes()).isEqualTo(content);
        }
        return UploadPartResponse.builder().eTag("etag-1").build();
      });

    StorageProperties.Minio properties = new StorageProperties.Minio();
    properties.setBucket("sts-test");
    AwsS3StorageGateway gateway = new AwsS3StorageGateway(client, properties);

    assertThat(gateway.uploadPart("source/test.bin", "provider-upload", 1,
                                  new ByteArrayInputStream(content), content.length))
      .isEqualTo("etag-1");
  }

  @Test
  void multipartReadStopsAfterDetectingContentLargerThanDeclaredSize() {
    S3Client client = mock(S3Client.class);
    when(client.headBucket(any(HeadBucketRequest.class))).thenReturn(HeadBucketResponse.builder().build());
    StorageProperties.Minio properties = new StorageProperties.Minio();
    properties.setBucket("sts-test");
    AwsS3StorageGateway gateway = new AwsS3StorageGateway(client, properties);
    InputStream content = new InputStream() {
      private int position;

      @Override
      public int read() throws IOException {
        if (position < 3) return "abc".charAt(position++);
        throw new IOException("must not read beyond declared size plus one byte");
      }
    };

    assertThatThrownBy(() -> gateway.uploadPart("source/test.bin", "provider-upload", 1, content, 2))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("实际大小");
  }

  @Test
  void multipartRejectsContentShorterThanDeclaredSize() {
    S3Client client = mock(S3Client.class);
    when(client.headBucket(any(HeadBucketRequest.class))).thenReturn(HeadBucketResponse.builder().build());
    StorageProperties.Minio properties = new StorageProperties.Minio();
    properties.setBucket("sts-test");
    AwsS3StorageGateway gateway = new AwsS3StorageGateway(client, properties);

    assertThatThrownBy(() -> gateway.uploadPart("source/test.bin", "provider-upload", 1,
                                                new ByteArrayInputStream(new byte[]{1, 2}), 3))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("实际大小");
  }
}

package com.sts.admin.storage;

import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

public class AwsS3StorageGateway implements S3StorageGateway {
  private final S3Client client;
  private final String bucket;

  public AwsS3StorageGateway(S3Client client, StorageProperties.Minio properties) {
    this.client = client;
    this.bucket = properties.getBucket();
    initializeBucket(properties.isAutoCreateBucket());
  }

  private void initializeBucket(boolean autoCreate) {
    try {
      client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
    } catch (NoSuchBucketException e) {
      if (!autoCreate) throw new IllegalStateException("MinIO bucket does not exist: " + bucket, e);
      client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
    } catch (S3Exception e) {
      if (e.statusCode() == 404 && autoCreate) {
        client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
        return;
      }
      throw new IllegalStateException("MinIO bucket is not accessible: " + bucket, e);
    }
  }

  @Override
  public void putObject(String objectName, InputStream input, long size, String contentType) {
    client.putObject(PutObjectRequest.builder().bucket(bucket).key(objectName)
                       .contentType(contentType).build(), RequestBody.fromInputStream(input, size));
  }

  @Override
  public StorageObject getObject(String objectName) {
    ResponseInputStream<GetObjectResponse> response = client.getObject(
      GetObjectRequest.builder().bucket(bucket).key(objectName).build());
    return new StorageObject(response, response.response().contentLength(), response.response().contentType());
  }

  @Override
  public StorageObject getObjectRange(String objectName, long start, long end) {
    ResponseInputStream<GetObjectResponse> response = client.getObject(
      GetObjectRequest.builder().bucket(bucket).key(objectName)
        .range("bytes=" + start + "-" + end).build());
    return new StorageObject(response, end - start + 1, response.response().contentType());
  }

  @Override
  public void deleteObject(String objectName) {
    client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(objectName).build());
  }

  @Override
  public String createMultipartUpload(String objectName, String contentType) {
    return client.createMultipartUpload(CreateMultipartUploadRequest.builder()
                                          .bucket(bucket).key(objectName).contentType(contentType).build()).uploadId();
  }

  @Override
  public String uploadPart(String objectName, String providerUploadId, int partNumber,
                           InputStream input, long size) {
    try {
      // 分片大小受业务层限制为 10MB；缓存在内存中可为 AWS SDK 的网络重试提供全新的输入流。
      if (size < 0 || size >= Integer.MAX_VALUE) {
        throw new IllegalArgumentException("分片声明大小超出可处理范围");
      }
      byte[] content = input.readNBytes(Math.toIntExact(size) + 1);
      if (content.length != size) {
        throw new IllegalArgumentException("分片实际大小与声明大小不一致");
      }
      return client.uploadPart(UploadPartRequest.builder().bucket(bucket).key(objectName)
                                 .uploadId(providerUploadId).partNumber(partNumber).contentLength(size).build(),
                               RequestBody.fromBytes(content)).eTag();
    } catch (IOException e) {
      throw new UncheckedIOException("读取待上传分片失败", e);
    }
  }

  @Override
  public List<UploadedStoragePart> listParts(String objectName, String providerUploadId) {
    List<UploadedStoragePart> result = new ArrayList<>();
    Integer marker = null;
    do {
      ListPartsResponse response = client.listParts(ListPartsRequest.builder().bucket(bucket).key(objectName)
                                                      .uploadId(providerUploadId).partNumberMarker(marker).build());
      response.parts().forEach(part -> result.add(
        new UploadedStoragePart(part.partNumber(), part.eTag(), part.size())));
      marker = response.isTruncated() ? response.nextPartNumberMarker() : null;
    } while (marker != null);
    return result;
  }

  @Override
  public void completeMultipartUpload(String objectName, String providerUploadId,
                                      List<CompletedStoragePart> parts) {
    List<CompletedPart> completed = parts.stream()
      .map(part -> CompletedPart.builder().partNumber(part.partNumber()).eTag(part.etag()).build())
      .toList();
    client.completeMultipartUpload(CompleteMultipartUploadRequest.builder().bucket(bucket).key(objectName)
                                     .uploadId(providerUploadId).multipartUpload(CompletedMultipartUpload.builder().parts(
        completed).build()).build());
  }

  @Override
  public void abortMultipartUpload(String objectName, String providerUploadId) {
    client.abortMultipartUpload(AbortMultipartUploadRequest.builder().bucket(bucket).key(objectName)
                                  .uploadId(providerUploadId).build());
  }

  @Override
  public long objectSize(String objectName) {
    return client.headObject(HeadObjectRequest.builder().bucket(bucket).key(objectName).build()).contentLength();
  }

  @Override
  public String sha256(String objectName) {
    try (InputStream input = getObject(objectName).inputStream()) {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] buffer = new byte[1024 * 1024];
      int read;
      while ((read = input.read(buffer)) >= 0) {
        if (read > 0) digest.update(buffer, 0, read);
      }
      return HexFormat.of().formatHex(digest.digest());
    } catch (IOException e) {
      throw new UncheckedIOException("读取 MinIO 对象校验失败", e);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 algorithm is unavailable", e);
    }
  }

  @Override
  public String bucketName() {
    return bucket;
  }
}

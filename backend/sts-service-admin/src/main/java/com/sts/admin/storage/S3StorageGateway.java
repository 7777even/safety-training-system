package com.sts.admin.storage;

import java.io.InputStream;
import java.util.List;

public interface S3StorageGateway {
  void putObject(String objectName, InputStream input, long size, String contentType);

  StorageObject getObject(String objectName);

  StorageObject getObjectRange(String objectName, long start, long end);

  void deleteObject(String objectName);

  String createMultipartUpload(String objectName, String contentType);

  String uploadPart(String objectName, String providerUploadId, int partNumber, InputStream input, long size);

  List<UploadedStoragePart> listParts(String objectName, String providerUploadId);

  void completeMultipartUpload(String objectName, String providerUploadId, List<CompletedStoragePart> parts);

  void abortMultipartUpload(String objectName, String providerUploadId);

  long objectSize(String objectName);

  String sha256(String objectName);

  String bucketName();
}

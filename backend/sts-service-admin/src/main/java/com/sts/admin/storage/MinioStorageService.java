package com.sts.admin.storage;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

@RequiredArgsConstructor
public class MinioStorageService implements StorageService {
  private final S3StorageGateway gateway;
  private final TenantObjectKeyService objectKeys;

  @Override
  public FileInfo upload(MultipartFile file, String folder) {
    String objectName = objectKeys.forCurrentTenant(generatedObjectName(folder, file.getOriginalFilename()));
    try {
      gateway.putObject(objectName, file.getInputStream(), file.getSize(), file.getContentType());
    } catch (IOException e) {
      throw new UncheckedIOException("文件读取失败", e);
    }
    return new FileInfo(objectName, file.getOriginalFilename(), file.getSize(), file.getContentType());
  }

  @Override
  public Resource download(String fileKey) {
    StorageObject object = gateway.getObject(fileKey);
    return new InputStreamResource(object.inputStream()) {
      @Override
      public long contentLength() {
        return object.size();
      }
    };
  }

  @Override
  public void delete(String fileKey) {
    gateway.deleteObject(fileKey);
  }

  @Override
  public String getUrl(String fileKey) {
    return "minio-private://" + fileKey;
  }

  public static String generatedObjectName(String originalName) {
    return generatedObjectName("source", originalName);
  }

  public static String generatedObjectName(String folder, String originalName) {
    String extension = safeExtension(originalName);
    String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM"));
    String prefix = "course-cover".equals(folder) ? "course-cover" : "source";
    return prefix + "/" + date + "/" + UUID.randomUUID() + extension;
  }

  private static String safeExtension(String name) {
    if (name == null) return "";
    int dot = name.lastIndexOf('.');
    if (dot < 0 || dot == name.length() - 1) return "";
    String ext = name.substring(dot).toLowerCase(Locale.ROOT);
    return ext.matches("\\.[a-z0-9]{1,10}") ? ext : "";
  }
}

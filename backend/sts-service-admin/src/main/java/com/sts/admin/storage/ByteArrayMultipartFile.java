package com.sts.admin.storage;

import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

/**
 * 将内存字节作为 MultipartFile 交给 {@link StorageService#upload}，用于系统生成的证书和档案文件。
 */
public final class ByteArrayMultipartFile implements MultipartFile {

  private final String name;
  private final String originalFilename;
  private final String contentType;
  private final byte[] content;

  public ByteArrayMultipartFile(String originalFilename, String contentType, byte[] content) {
    this.name = "file";
    this.originalFilename = originalFilename;
    this.contentType = contentType;
    this.content = content == null ? new byte[0] : content;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public String getOriginalFilename() {
    return originalFilename;
  }

  @Override
  public String getContentType() {
    return contentType;
  }

  @Override
  public boolean isEmpty() {
    return content.length == 0;
  }

  @Override
  public long getSize() {
    return content.length;
  }

  @Override
  public byte[] getBytes() {
    return content;
  }

  @Override
  public InputStream getInputStream() {
    return new ByteArrayInputStream(content);
  }

  @Override
  public void transferTo(File dest) throws IOException {
    Files.write(dest.toPath(), content);
  }
}

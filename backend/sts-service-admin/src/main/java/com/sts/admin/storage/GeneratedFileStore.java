package com.sts.admin.storage;

import com.sts.admin.entity.SysFileInfo;
import com.sts.admin.mapper.SysFileInfoMapper;
import com.sts.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 将系统生成的字节写入对象存储并登记 {@code us_sys_file_info}，供证书和档案下载。
 */
@Service
@RequiredArgsConstructor
public class GeneratedFileStore {

  private final StorageService storageService;
  private final SysFileInfoMapper fileInfoMapper;

  /**
   * 保存生成文件并返回文件主键；不得使用占位 ID。
   */
  public Long store(byte[] content, String fileName, String folder, String contentType, String suffix) {
    FileInfo stored = storageService.upload(new ByteArrayMultipartFile(fileName, contentType, content), folder);
    SysFileInfo entity = new SysFileInfo();
    entity.setFileName(fileName);
    entity.setBucketName(folder);
    entity.setObjectName(stored.fileKey());
    entity.setFileSize(stored.size());
    entity.setFileSuffix(suffix);
    entity.setFileUrl(storageService.getUrl(stored.fileKey()));
    try {
      fileInfoMapper.insert(entity);
    } catch (RuntimeException error) {
      try {
        storageService.delete(stored.fileKey());
      } catch (RuntimeException cleanupError) {
        error.addSuppressed(cleanupError);
      }
      throw error;
    }
    return entity.getFileInfoId();
  }

  /**
   * 按文件主键打开真实对象；记录或对象缺失时返回 404。
   */
  public StoredDownload open(Long fileInfoId, int notFoundCode, String notFoundMessage) {
    if (fileInfoId == null) {
      throw new BusinessException(HttpStatus.NOT_FOUND, notFoundCode, notFoundMessage);
    }
    SysFileInfo file = fileInfoMapper.selectById(fileInfoId);
    if (file == null || !StringUtils.hasText(file.getObjectName())) {
      throw new BusinessException(HttpStatus.NOT_FOUND, notFoundCode, notFoundMessage);
    }
    Resource resource = storageService.download(file.getObjectName());
    if (resource == null || !resource.exists()) {
      throw new BusinessException(HttpStatus.NOT_FOUND, notFoundCode, notFoundMessage);
    }
    long size = file.getFileSize() == null ? 0L : file.getFileSize();
    return new StoredDownload(resource, file.getFileName(), contentType(file.getFileSuffix()), size);
  }

  private static String contentType(String suffix) {
    if ("png".equalsIgnoreCase(suffix)) {
      return "image/png";
    }
    if ("json".equalsIgnoreCase(suffix)) {
      return "application/json";
    }
    if ("pdf".equalsIgnoreCase(suffix)) {
      return "application/pdf";
    }
    return "application/octet-stream";
  }
}

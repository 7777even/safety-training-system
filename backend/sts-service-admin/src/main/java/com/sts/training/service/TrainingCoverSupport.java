package com.sts.training.service;

import com.sts.admin.entity.SysFileInfo;
import com.sts.admin.mapper.SysFileInfoMapper;
import com.sts.admin.storage.StorageService;
import com.sts.common.exception.BusinessException;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;

/**
 * 按封面文件 ID 打开培训封面对象，将缺失映射为 8164。
 */
final class TrainingCoverSupport {

  static final int COVER_NOT_FOUND = 8164;

  private TrainingCoverSupport() {
  }

  static TrainingCoverFile open(SysFileInfoMapper fileInfoMapper, StorageService storageService, Long coverFileId) {
    if (coverFileId == null) {
      throw coverNotFound();
    }
    SysFileInfo file = fileInfoMapper.selectById(coverFileId);
    if (file == null || !StringUtils.hasText(file.getObjectName())) {
      throw coverNotFound();
    }
    Resource resource;
    try {
      resource = storageService.download(file.getObjectName());
      if (resource == null || !resource.exists()) {
        throw coverNotFound();
      }
    } catch (BusinessException error) {
      throw error;
    } catch (RuntimeException error) {
      throw coverNotFound();
    }
    long fileSize = file.getFileSize() == null ? 0L : file.getFileSize();
    int version = file.getVersion() == null ? 0 : file.getVersion();
    return new TrainingCoverFile(resource, file.getFileName(), contentType(file.getFileSuffix()), fileSize,
                                 "\"" + file.getFileInfoId() + "-" + version + "-" + fileSize + "\"");
  }

  static BusinessException coverNotFound() {
    return new BusinessException(HttpStatus.NOT_FOUND, COVER_NOT_FOUND, "培训封面不存在");
  }

  private static String contentType(String suffix) {
    String key = suffix == null ? "" : suffix.toLowerCase(java.util.Locale.ROOT);
    if ("jpg".equals(key) || "jpeg".equals(key)) {
      return "image/jpeg";
    }
    if ("png".equals(key)) {
      return "image/png";
    }
    if ("webp".equals(key)) {
      return "image/webp";
    }
    return "application/octet-stream";
  }
}

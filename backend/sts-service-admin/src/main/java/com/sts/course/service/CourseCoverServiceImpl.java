package com.sts.course.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sts.admin.entity.SysFileInfo;
import com.sts.admin.mapper.SysFileInfoMapper;
import com.sts.admin.storage.FileInfo;
import com.sts.admin.storage.StorageService;
import com.sts.admin.storage.TenantObjectKeyService;
import com.sts.admin.tenant.TenantContext;
import com.sts.common.exception.BusinessException;
import com.sts.course.entity.Course;
import com.sts.course.mapper.CourseMapper;
import com.sts.course.vo.CourseCoverUploadVO;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class CourseCoverServiceImpl implements CourseCoverService {

  static final long MAX_FILE_SIZE = 10L * 1024 * 1024;
  private static final String COVER_FOLDER = "course-cover";
  private static final int INVALID_UPLOAD_CODE = 5090;
  private static final int INVALID_REFERENCE_CODE = 5091;
  private static final int COVER_NOT_FOUND_CODE = 5092;
  private static final Pattern COVER_OBJECT_NAME = Pattern.compile(
    "^(?:[A-Z0-9_-]{2,64}/)?course-cover/\\d{4}/\\d{2}/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(?:jpg|jpeg|png|webp)$");
  private static final Map<String, String> CONTENT_TYPES = Map.of(
    "jpg", "image/jpeg",
    "jpeg", "image/jpeg",
    "png", "image/png",
    "webp", "image/webp");

  private final SysFileInfoMapper fileInfoMapper;
  private final CourseMapper courseMapper;
  private final StorageService storageService;
  private final TenantObjectKeyService objectKeys;

  @Override
  @Transactional
  public CourseCoverUploadVO upload(MultipartFile file) {
    ValidatedImage image = validateImage(file);
    FileInfo stored = storageService.upload(file, COVER_FOLDER);
    SysFileInfo entity = new SysFileInfo();
    entity.setFileName(safeOriginalFileName(file.getOriginalFilename(), image.suffix()));
    entity.setBucketName(COVER_FOLDER);
    entity.setObjectName(stored.fileKey());
    entity.setFileSize(stored.size());
    entity.setFileSuffix(image.suffix());
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

    CourseCoverUploadVO result = new CourseCoverUploadVO();
    result.setFileInfoId(entity.getFileInfoId());
    result.setFileName(entity.getFileName());
    result.setFileSize(entity.getFileSize());
    result.setContentType(image.contentType());
    return result;
  }

  @Override
  public void validateReference(Long fileInfoId) {
    requireValidCoverFile(fileInfoId);
  }

  @Override
  public CourseCoverFile open(Long courseId) {
    Course course = courseMapper.selectById(courseId);
    Long tenantId = TenantContext.requireTenantId();
    if (course == null || !Objects.equals(course.getOrganizationId(), tenantId)) {
      throw new BusinessException(HttpStatus.NOT_FOUND, 5011, "课程不存在");
    }
    if (course.getCoverFileId() == null) {
      throw new BusinessException(HttpStatus.NOT_FOUND, COVER_NOT_FOUND_CODE, "课程封面不存在");
    }
    SysFileInfo file = findValidCoverFile(course.getCoverFileId());
    if (file == null) {
      throw new BusinessException(HttpStatus.NOT_FOUND, COVER_NOT_FOUND_CODE, "课程封面不存在");
    }
    long fileSize = file.getFileSize() == null ? 0L : file.getFileSize();
    int version = file.getVersion() == null ? 0 : file.getVersion();
    String etag = "\"" + file.getFileInfoId() + "-" + version + "-" + fileSize + "\"";
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
    return new CourseCoverFile(resource, file.getFileName(), contentType(file.getFileSuffix()), fileSize, etag);
  }

  private SysFileInfo requireValidCoverFile(Long fileInfoId) {
    SysFileInfo file = findValidCoverFile(fileInfoId);
    if (file == null) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, INVALID_REFERENCE_CODE, "课程封面文件无效");
    }
    return file;
  }

  private SysFileInfo findValidCoverFile(Long fileInfoId) {
    SysFileInfo file = fileInfoId == null ? null : fileInfoMapper.selectOne(
      new LambdaQueryWrapper<SysFileInfo>().eq(SysFileInfo::getFileInfoId, fileInfoId));
    if (file == null
      || !COVER_FOLDER.equals(file.getBucketName())
      || file.getObjectName() == null
      || !COVER_OBJECT_NAME.matcher(file.getObjectName()).matches()
      || !objectKeys.isLegacyOrCurrentTenantKey(file.getObjectName(), COVER_FOLDER + "/")
      || !CONTENT_TYPES.containsKey(normalize(file.getFileSuffix()))
      || !file.getObjectName().endsWith("." + normalize(file.getFileSuffix()))) {
      return null;
    }
    return file;
  }

  private static ValidatedImage validateImage(MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw invalidUpload("课程封面不能为空");
    }
    if (file.getSize() > MAX_FILE_SIZE) {
      throw invalidUpload("课程封面不能超过 10MB");
    }
    String suffix = suffix(file.getOriginalFilename());
    String expectedContentType = CONTENT_TYPES.get(suffix);
    if (expectedContentType == null || !expectedContentType.equals(normalizeContentType(file.getContentType()))) {
      throw invalidUpload("课程封面仅支持 JPG、JPEG、PNG 或 WebP 图片");
    }
    try {
      byte[] bytes = file.getBytes();
      if (!matchesSignature(suffix, bytes)) {
        throw invalidUpload("课程封面格式与文件内容不一致");
      }
    } catch (IOException error) {
      throw new UncheckedIOException("课程封面读取失败", error);
    }
    return new ValidatedImage(suffix, expectedContentType);
  }

  private static boolean matchesSignature(String suffix, byte[] bytes) {
    if ("png".equals(suffix)) {
      return bytes.length >= 8
        && (bytes[0] & 0xff) == 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4e && bytes[3] == 0x47
        && bytes[4] == 0x0d && bytes[5] == 0x0a && bytes[6] == 0x1a && bytes[7] == 0x0a;
    }
    if ("jpg".equals(suffix) || "jpeg".equals(suffix)) {
      return bytes.length >= 3
        && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8 && (bytes[2] & 0xff) == 0xff;
    }
    return bytes.length >= 12
      && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
      && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P';
  }

  private static String contentType(String suffix) {
    return CONTENT_TYPES.getOrDefault(normalize(suffix), "application/octet-stream");
  }

  private static String suffix(String fileName) {
    if (fileName == null) {
      return "";
    }
    int dot = fileName.lastIndexOf('.');
    return dot < 0 || dot == fileName.length() - 1 ? "" : normalize(fileName.substring(dot + 1));
  }

  private static String normalize(String value) {
    return value == null ? "" : value.toLowerCase(Locale.ROOT);
  }

  private static String normalizeContentType(String value) {
    if (value == null) {
      return "";
    }
    int separator = value.indexOf(';');
    return normalize(separator < 0 ? value.trim() : value.substring(0, separator).trim());
  }

  private static String safeOriginalFileName(String value, String suffix) {
    String name = value == null ? "" : value.replace('\\', '/');
    name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "_").trim();
    return name.isEmpty() ? "course-cover." + suffix : name;
  }

  private static BusinessException invalidUpload(String message) {
    return new BusinessException(HttpStatus.BAD_REQUEST, INVALID_UPLOAD_CODE, message);
  }

  private static BusinessException coverNotFound() {
    return new BusinessException(HttpStatus.NOT_FOUND, COVER_NOT_FOUND_CODE, "课程封面不存在");
  }

  private record ValidatedImage(String suffix, String contentType) {
  }
}

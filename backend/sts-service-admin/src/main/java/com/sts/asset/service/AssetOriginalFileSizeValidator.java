package com.sts.asset.service;

import com.sts.common.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 校验素材原始文件大小：音视频 2GB、文档 512MB、图片 16MB。
 */
@Component
public class AssetOriginalFileSizeValidator {
  public static final int UPLOAD_ERROR_CODE = 5007;
  public static final int CREATE_ERROR_CODE = 6025;

  static final long AUDIO_VIDEO_MAX_BYTES = 2L * 1024 * 1024 * 1024;
  static final long DOCUMENT_MAX_BYTES = 512L * 1024 * 1024;
  static final long IMAGE_MAX_BYTES = 16L * 1024 * 1024;

  private static final String ASSET_FOLDER = "asset";
  private static final Map<String, Long> MAX_BYTES_BY_TYPE = Map.of(
    "AUDIO", AUDIO_VIDEO_MAX_BYTES,
    "VIDEO", AUDIO_VIDEO_MAX_BYTES,
    "DOCUMENT", DOCUMENT_MAX_BYTES,
    "IMAGE", IMAGE_MAX_BYTES);

  private static final Set<String> IMAGE_SUFFIXES = Set.of(
    "jpg", "jpeg", "png", "gif", "webp", "bmp", "svg");
  private static final Set<String> AUDIO_SUFFIXES = Set.of(
    "mp3", "wav", "m4a", "aac", "ogg", "flac");
  private static final Set<String> VIDEO_SUFFIXES = Set.of(
    "mp4", "webm", "mov", "avi", "mkv", "m3u8", "wmv", "flv", "ts", "asf", "rm", "rmvb", "mpg", "mpeg", "3gp");
  private static final Set<String> DOCUMENT_SUFFIXES = Set.of(
    "pdf", "ppt", "pptx", "doc", "docx", "xls", "xlsx", "csv", "txt", "md");

  /**
   * 按素材类型校验原始文件字节数；无对应上限的类型直接放行。
   */
  public void validateByType(String assetType, Long fileSize, int errorCode) {
    if (!StringUtils.hasText(assetType) || fileSize == null) {
      return;
    }
    Long maxBytes = MAX_BYTES_BY_TYPE.get(assetType);
    if (maxBytes == null || fileSize <= maxBytes) {
      return;
    }
    throw new BusinessException(HttpStatus.BAD_REQUEST, errorCode, messageFor(assetType));
  }

  /**
   * 素材目录上传按文件名或 MIME 推断类型后校验；其他目录不拦截。
   */
  public void validateAssetFolderUpload(String folder, String fileName, String contentType, long fileSize) {
    if (!ASSET_FOLDER.equals(folder)) {
      return;
    }
    validateByType(inferAssetType(fileName, contentType), fileSize, UPLOAD_ERROR_CODE);
  }

  private static String inferAssetType(String fileName, String contentType) {
    String suffix = suffix(fileName);
    if (IMAGE_SUFFIXES.contains(suffix)) {
      return "IMAGE";
    }
    if (AUDIO_SUFFIXES.contains(suffix)) {
      return "AUDIO";
    }
    if (VIDEO_SUFFIXES.contains(suffix)) {
      return "VIDEO";
    }
    if (DOCUMENT_SUFFIXES.contains(suffix)) {
      return "DOCUMENT";
    }
    if (contentType == null) {
      return null;
    }
    String lower = contentType.toLowerCase(Locale.ROOT);
    if (lower.startsWith("image/")) {
      return "IMAGE";
    }
    if (lower.startsWith("audio/")) {
      return "AUDIO";
    }
    if (lower.startsWith("video/")) {
      return "VIDEO";
    }
    if (lower.equals("application/pdf")
      || lower.contains("officedocument")
      || lower.contains("msword")
      || lower.contains("ms-excel")
      || lower.contains("ms-powerpoint")) {
      return "DOCUMENT";
    }
    return null;
  }

  private static String suffix(String fileName) {
    if (fileName == null) {
      return "";
    }
    int dot = fileName.lastIndexOf('.');
    if (dot < 0 || dot == fileName.length() - 1) {
      return "";
    }
    return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
  }

  private static String messageFor(String assetType) {
    return switch (assetType) {
      case "IMAGE" -> "图片原始文件不能超过 16MB";
      case "DOCUMENT" -> "文档原始文件不能超过 512MB";
      default -> "音视频原始文件不能超过 2GB";
    };
  }
}

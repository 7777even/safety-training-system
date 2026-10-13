package com.sts.course.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import com.sts.course.service.CourseCoverService;
import com.sts.course.vo.CourseCoverUploadVO;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * 课程封面上传与受控读取接口。
 */
@RestController
@RequiredArgsConstructor
public class CourseCoverController {

  private final CourseCoverService coverService;

  /**
   * 上传课程封面。
   */
  @PostMapping("/course/covers")
  @SaCheckPermission(value = {"course:course:add", "course:course:edit"}, mode = SaMode.OR)
  public CourseCoverUploadVO upload(@RequestParam(value = "file", required = false) MultipartFile file) {
    return coverService.upload(file);
  }

  /**
   * 查看或下载当前课程封面。
   */
  @GetMapping("/course/courses/{courseId}/cover")
  public ResponseEntity<Resource> cover(
    @PathVariable Long courseId,
    @RequestParam(value = "download", defaultValue = "false") boolean download) {
    CourseCoverService.CourseCoverFile cover = coverService.open(courseId);
    ContentDisposition disposition = (download ? ContentDisposition.attachment() : ContentDisposition.inline())
      .filename(safeFileName(cover.fileName(), courseId), StandardCharsets.UTF_8)
      .build();
    return ResponseEntity.ok()
      .contentType(MediaType.parseMediaType(cover.contentType()))
      .contentLength(cover.fileSize())
      .eTag(cover.etag())
      .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
      .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePrivate())
      .body(cover.resource());
  }

  private static String safeFileName(String value, Long courseId) {
    String name = value == null ? "" : value.replace('\\', '/');
    name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "_").trim();
    return name.isEmpty() ? "course-cover-" + courseId : name;
  }
}

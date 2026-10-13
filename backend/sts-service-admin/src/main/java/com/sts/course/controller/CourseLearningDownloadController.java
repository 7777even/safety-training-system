package com.sts.course.controller;

import com.sts.asset.service.UnsatisfiableRangeException;
import com.sts.course.service.LearnerCourseDownloadService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.nio.charset.StandardCharsets;

/**
 * 学员课程附件受控下载接口。
 */
@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(name = "storage.type", havingValue = "minio")
public class CourseLearningDownloadController {

  private final LearnerCourseDownloadService downloadService;

  /**
   * 下载本人报名课程的冻结附件源文件。
   */
  @GetMapping("/course/learning/enrollments/{enrollmentId}/download-assets/{assetId}/file")
  public ResponseEntity<StreamingResponseBody> file(
    @PathVariable Long enrollmentId, @PathVariable Long assetId,
    @RequestHeader(value = HttpHeaders.RANGE, required = false) String range) {
    try {
      LearnerCourseDownloadService.DownloadFile download =
        downloadService.open(enrollmentId, assetId, range);
      var media = download.media();
      var object = media.object();
      HttpHeaders headers = new HttpHeaders();
      headers.set(HttpHeaders.ACCEPT_RANGES, "bytes");
      headers.setContentLength(object.size());
      headers.setContentType(safeContentType(object.contentType()));
      headers.setContentDisposition(ContentDisposition.attachment()
                                      .filename(safeFileName(download.fileName(), assetId), StandardCharsets.UTF_8)
                                      .build());
      if (media.range() != null) {
        headers.set(HttpHeaders.CONTENT_RANGE, "bytes " + media.range().start() + "-"
          + media.range().end() + "/" + media.totalSize());
      }
      StreamingResponseBody body = output -> {
        try (var input = object.inputStream()) {
          input.transferTo(output);
        }
      };
      return new ResponseEntity<>(body, headers,
                                  media.range() == null ? HttpStatus.OK : HttpStatus.PARTIAL_CONTENT);
    } catch (UnsatisfiableRangeException e) {
      HttpHeaders headers = new HttpHeaders();
      headers.set(HttpHeaders.CONTENT_RANGE, "bytes */" + e.size());
      return new ResponseEntity<>(null, headers, HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE);
    }
  }

  private static String safeFileName(String value, Long assetId) {
    String name = value == null ? "" : value.replace('\\', '/');
    name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "_").trim();
    return name.isEmpty() ? "attachment-" + assetId : name;
  }

  private static MediaType safeContentType(String value) {
    if (value == null || !value.matches("[a-zA-Z0-9.+-]+/[a-zA-Z0-9.+-]+")) {
      return MediaType.APPLICATION_OCTET_STREAM;
    }
    return MediaType.parseMediaType(value);
  }
}

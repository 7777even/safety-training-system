package com.sts.course.controller;

import com.sts.admin.storage.StorageObject;
import com.sts.asset.service.AssetMediaAccessService;
import com.sts.asset.service.ByteRange;
import com.sts.asset.service.UnsatisfiableRangeException;
import com.sts.course.service.LearnerCourseDownloadService;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CourseLearningDownloadControllerTest {

  @Test
  void fullAndRangeDownloadsUseSafeHeaders() {
    LearnerCourseDownloadService service = mock(LearnerCourseDownloadService.class);
    CourseLearningDownloadController controller = new CourseLearningDownloadController(service);
    when(service.open(11L, 30L, null)).thenReturn(download("消防安全手册.pdf", null));
    when(service.open(11L, 30L, "bytes=2-5"))
      .thenReturn(download("消防安全手册.pdf", new ByteRange(2, 5)));

    var full = controller.file(11L, 30L, null);
    assertThat(full.getStatusCode().value()).isEqualTo(200);
    assertThat(full.getHeaders().getFirst("Content-Disposition"))
      .contains("attachment").contains("filename*=UTF-8''");
    var partial = controller.file(11L, 30L, "bytes=2-5");
    assertThat(partial.getStatusCode().value()).isEqualTo(206);
    assertThat(partial.getHeaders().getFirst("Content-Range")).isEqualTo("bytes 2-5/10");
  }

  @Test
  void invalidRangeReturns416() {
    LearnerCourseDownloadService service = mock(LearnerCourseDownloadService.class);
    when(service.open(11L, 30L, "bytes=20-"))
      .thenThrow(new UnsatisfiableRangeException(10));
    var response = new CourseLearningDownloadController(service).file(11L, 30L, "bytes=20-");
    assertThat(response.getStatusCode().value()).isEqualTo(416);
    assertThat(response.getHeaders().getFirst("Content-Range")).isEqualTo("bytes */10");
  }

  @Test
  void unsafeFileNameIsReducedToSafeLeafName() {
    LearnerCourseDownloadService service = mock(LearnerCourseDownloadService.class);
    when(service.open(11L, 30L, null)).thenReturn(download("../../手册\r\n.pdf", null));

    var disposition = new CourseLearningDownloadController(service).file(11L, 30L, null)
      .getHeaders().getFirst("Content-Disposition");

    assertThat(disposition).doesNotContain("..", "\r", "\n").contains("attachment");
  }

  private static LearnerCourseDownloadService.DownloadFile download(String fileName, ByteRange range) {
    byte[] bytes = range == null ? "0123456789".getBytes() : "2345".getBytes();
    var media = new AssetMediaAccessService.MediaObject(
      new StorageObject(new ByteArrayInputStream(bytes), bytes.length, "application/pdf"), 10, range);
    return new LearnerCourseDownloadService.DownloadFile(fileName, media);
  }
}

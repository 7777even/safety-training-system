package com.sts.course.controller;

import com.sts.course.service.CourseCoverService;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CourseCoverControllerTest {

  @Test
  void coverDefaultsToInlineAndSupportsAttachmentDownload() throws Exception {
    CourseCoverService service = mock(CourseCoverService.class);
    when(service.open(10L)).thenReturn(new CourseCoverService.CourseCoverFile(
      new ByteArrayResource("png".getBytes()), "安全课程.png", "image/png", 3L, "\"88-0-3\""));
    CourseCoverController controller = new CourseCoverController(service);

    var inline = controller.cover(10L, false);
    var attachment = controller.cover(10L, true);

    assertThat(inline.getHeaders().getContentDisposition().getType()).isEqualTo("inline");
    assertThat(attachment.getHeaders().getContentDisposition().getType()).isEqualTo("attachment");
    assertThat(inline.getHeaders().getContentType().toString()).isEqualTo("image/png");
    assertThat(inline.getHeaders().getETag()).isEqualTo("\"88-0-3\"");
    assertThat(inline.getHeaders().getContentLength()).isEqualTo(3L);
    assertThat(inline.getBody()).isNotNull();
  }
}

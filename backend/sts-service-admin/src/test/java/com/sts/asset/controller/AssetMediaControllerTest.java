package com.sts.asset.controller;

import com.sts.admin.storage.StorageObject;
import com.sts.asset.service.AssetMediaAccessService;
import com.sts.asset.service.ByteRange;
import com.sts.asset.service.UnsatisfiableRangeException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AssetMediaControllerTest {
  @Test
  void fullAndRangeResponsesUse200And206() {
    AssetMediaAccessService service = mock(AssetMediaAccessService.class);
    AssetMediaController controller = new AssetMediaController(service);
    when(service.file(20L, 30L, null)).thenReturn(media("0123456789", null));
    when(service.file(20L, 30L, "bytes=2-5")).thenReturn(
      new AssetMediaAccessService.MediaObject(
        new StorageObject(new ByteArrayInputStream("2345".getBytes()), 4, "audio/mpeg"),
        10, new ByteRange(2, 5)));

    assertThat(controller.file(20L, 30L, null).getStatusCode().value()).isEqualTo(200);
    var partial = controller.file(20L, 30L, "bytes=2-5");
    assertThat(partial.getStatusCode().value()).isEqualTo(206);
    assertThat(partial.getHeaders().getFirst("Content-Range")).isEqualTo("bytes 2-5/10");
  }

  @Test
  void invalidRangeReturns416WithActualSize() {
    AssetMediaAccessService service = mock(AssetMediaAccessService.class);
    when(service.file(20L, 30L, "bytes=20-")).thenThrow(new UnsatisfiableRangeException(10));
    var response = new AssetMediaController(service).file(20L, 30L, "bytes=20-");
    assertThat(response.getStatusCode().value()).isEqualTo(416);
    assertThat(response.getHeaders().getFirst("Content-Range")).isEqualTo("bytes */10");
  }

  private static AssetMediaAccessService.MediaObject media(String text, ByteRange range) {
    byte[] bytes = text.getBytes();
    return new AssetMediaAccessService.MediaObject(
      new StorageObject(new ByteArrayInputStream(bytes), bytes.length, "audio/mpeg"), bytes.length, range);
  }
}

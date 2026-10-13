package com.sts.certificate.service;

import com.sts.admin.storage.GeneratedFileStore;
import com.sts.admin.storage.StoredDownload;
import com.sts.certificate.entity.CertificateTemplateVersion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CertificateImageRendererTest {

  @Test
  @DisplayName("render：真实背景图片参与证书渲染")
  void render_usesRealBackground() throws Exception {
    GeneratedFileStore fileStore = mock(GeneratedFileStore.class);
    when(fileStore.open(9L, 9404, "证书模板背景图片不存在"))
      .thenReturn(new StoredDownload(new ByteArrayResource(png(Color.BLUE)), "background.png", "image/png", 1));
    CertificateImageRenderer renderer = new CertificateImageRenderer(fileStore);
    CertificateTemplateVersion version = new CertificateTemplateVersion();
    version.setBackgroundFileId(9L);
    version.setWidthPx(80);
    version.setHeightPx(60);

    byte[] rendered = renderer.render(version, List.of(), Map.of());

    BufferedImage image = ImageIO.read(new ByteArrayInputStream(rendered));
    assertThat(new Color(image.getRGB(40, 30))).isEqualTo(Color.BLUE);
  }

  private static byte[] png(Color color) throws Exception {
    BufferedImage image = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
    var graphics = image.createGraphics();
    graphics.setColor(color);
    graphics.fillRect(0, 0, 10, 10);
    graphics.dispose();
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ImageIO.write(image, "png", out);
    return out.toByteArray();
  }
}

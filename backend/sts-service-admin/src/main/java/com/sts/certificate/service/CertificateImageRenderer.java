package com.sts.certificate.service;

import com.sts.admin.storage.GeneratedFileStore;
import com.sts.certificate.entity.CertificateTemplateElement;
import com.sts.certificate.entity.CertificateTemplateVersion;
import com.sts.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;

/**
 * 按模板版本尺寸和元素坐标渲染证书 PNG，不引入额外图像库。
 */
@Component
@RequiredArgsConstructor
public class CertificateImageRenderer {

  private static final int DEFAULT_WIDTH = 900;
  private static final int DEFAULT_HEIGHT = 600;
  private final GeneratedFileStore generatedFileStore;

  /**
   * 验证背景文件存在且能够解码为图片。
   */
  public void validateBackground(Long backgroundFileId) {
    background(backgroundFileId);
  }

  /**
   * 使用变量替换结果绘制证书底图与文字。
   */
  public byte[] render(CertificateTemplateVersion version, List<CertificateTemplateElement> elements,
                       Map<String, String> variables) {
    int width = version.getWidthPx() == null || version.getWidthPx() <= 0 ? DEFAULT_WIDTH : version.getWidthPx();
    int height = version.getHeightPx() == null || version.getHeightPx() <= 0 ? DEFAULT_HEIGHT : version.getHeightPx();
    BufferedImage source = background(version.getBackgroundFileId());
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = image.createGraphics();
    try {
      g.drawImage(source, 0, 0, width, height, null);
      g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
      if (elements == null || elements.isEmpty()) {
        drawText(g, "证书编号：" + value(variables, "CERT_NO"), 80, 180, 28);
        drawText(g, "学员姓名：" + value(variables, "HOLDER_NAME"), 80, 240, 28);
      } else {
        for (CertificateTemplateElement element : elements) {
          if (element == null || Integer.valueOf(0).equals(element.getStatus())) {
            continue;
          }
          String text = resolveText(element, variables);
          if (text == null || text.isBlank()) {
            continue;
          }
          int x = element.getXPosition() == null ? 40 : element.getXPosition();
          int y = element.getYPosition() == null ? 80 : element.getYPosition();
          int fontSize = element.getFontSize() == null || element.getFontSize() <= 0 ? 22 : element.getFontSize();
          g.setColor(parseColor(element.getFontColor()));
          drawText(g, text, x, y + fontSize, fontSize, element.getFontFamily());
        }
      }
    } finally {
      g.dispose();
    }
    try {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      ImageIO.write(image, "png", out);
      return out.toByteArray();
    } catch (IOException ex) {
      throw new UncheckedIOException("证书图片编码失败", ex);
    }
  }

  private static void drawText(Graphics2D g, String text, int x, int y, int fontSize) {
    drawText(g, text, x, y, fontSize, null);
  }

  private static void drawText(Graphics2D g, String text, int x, int y, int fontSize, String fontFamily) {
    String family = fontFamily == null || fontFamily.isBlank() ? Font.SANS_SERIF : fontFamily.trim();
    g.setFont(new Font(family, Font.PLAIN, fontSize));
    g.drawString(text, x, y);
  }

  private BufferedImage background(Long backgroundFileId) {
    try {
      var file = generatedFileStore.open(backgroundFileId, 9404, "证书模板背景图片不存在");
      try (var input = file.resource().getInputStream()) {
        BufferedImage image = ImageIO.read(input);
        if (image != null) {
          return image;
        }
      }
    } catch (BusinessException ex) {
      throw ex;
    } catch (IOException | RuntimeException ex) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 9404, "证书模板背景图片不可解码");
    }
    throw new BusinessException(HttpStatus.BAD_REQUEST, 9404, "证书模板背景图片不可解码");
  }

  private static String resolveText(CertificateTemplateElement element, Map<String, String> variables) {
    if ("VARIABLE".equals(element.getElementType())) {
      return value(variables, element.getVariableCode());
    }
    if ("TEXT".equals(element.getElementType()) || "IMAGE".equals(element.getElementType())) {
      return element.getStaticContent();
    }
    return element.getStaticContent();
  }

  private static String value(Map<String, String> variables, String key) {
    if (variables == null || key == null) {
      return "";
    }
    String value = variables.get(key);
    return value == null ? "" : value;
  }

  private static Color parseColor(String fontColor) {
    if (fontColor == null || fontColor.isBlank()) {
      return Color.DARK_GRAY;
    }
    String raw = fontColor.trim();
    if (raw.startsWith("#") && raw.length() == 7) {
      try {
        return new Color(Integer.parseInt(raw.substring(1), 16));
      } catch (NumberFormatException ignored) {
        return Color.DARK_GRAY;
      }
    }
    return Color.DARK_GRAY;
  }
}

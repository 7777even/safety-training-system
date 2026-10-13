package com.sts.asset.transcode;

import org.apache.pdfbox.Loader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

public class PdfPageCounter {
  public int count(InputStream input) {
    try {
      byte[] bytes = input.readAllBytes();
      try (var document = Loader.loadPDF(bytes)) {
        return document.getNumberOfPages();
      }
    } catch (IOException | RuntimeException e) {
      throw new TranscodeFailureException("SOURCE_INVALID", "PDF 文件无效或无法读取");
    }
  }

  public int count(Path path) {
    try (var document = Loader.loadPDF(path.toFile())) {
      return document.getNumberOfPages();
    } catch (Exception e) {
      throw new TranscodeFailureException("SOURCE_INVALID", "PDF 文件无效或无法读取");
    }
  }
}

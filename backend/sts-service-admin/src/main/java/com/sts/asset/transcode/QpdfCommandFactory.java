package com.sts.asset.transcode;

import java.nio.file.Path;
import java.util.List;

public class QpdfCommandFactory {
  private final String qpdfPath;

  public QpdfCommandFactory(String qpdfPath) {
    if (qpdfPath == null || !Path.of(qpdfPath).isAbsolute()) {
      throw new IllegalArgumentException("qpdf path must be absolute");
    }
    this.qpdfPath = qpdfPath;
  }

  public List<String> linearize(Path input, Path output) {
    return List.of(qpdfPath, "--linearize", input.toString(), output.toString());
  }

  public List<String> check(Path output) {
    return List.of(qpdfPath, "--check", output.toString());
  }

  public List<String> checkLinearization(Path output) {
    return List.of(qpdfPath, "--check-linearization", output.toString());
  }
}

package com.sts.asset.transcode;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QpdfCommandFactoryTest {

  @Test
  void buildsLinearizeAndValidationCommandsWithoutShell() {
    QpdfCommandFactory factory = new QpdfCommandFactory("/usr/bin/qpdf");
    Path input = Path.of("/tmp/source name.pdf");
    Path output = Path.of("/tmp/linearized name.pdf");

    assertThat(factory.linearize(input, output))
      .containsExactly("/usr/bin/qpdf", "--linearize", input.toString(), output.toString());
    assertThat(factory.check(output))
      .containsExactly("/usr/bin/qpdf", "--check", output.toString());
    assertThat(factory.checkLinearization(output))
      .containsExactly("/usr/bin/qpdf", "--check-linearization", output.toString());
  }

  @Test
  void rejectsRelativeExecutablePath() {
    assertThatThrownBy(() -> new QpdfCommandFactory("qpdf"))
      .isInstanceOf(IllegalArgumentException.class);
  }
}

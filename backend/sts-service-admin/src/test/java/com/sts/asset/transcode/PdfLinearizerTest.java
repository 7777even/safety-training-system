package com.sts.asset.transcode;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class PdfLinearizerTest {

  @TempDir
  Path tempDir;

  @Test
  void linearizesValidInputAndDeletesTaskDirectory() throws Exception {
    ExternalProcessRunner runner = mock(ExternalProcessRunner.class);
    QpdfCommandFactory commands = new QpdfCommandFactory("/usr/bin/qpdf");
    when(runner.run(any(), eq(Duration.ofSeconds(30)))).thenAnswer(invocation -> {
      List<String> command = invocation.getArgument(0);
      if (command.contains("--linearize")) {
        Files.copy(Path.of(command.get(2)), Path.of(command.get(3)));
      }
      return new ProcessResult(0, "ok");
    });
    PdfPageCounter pageCounter = mock(PdfPageCounter.class);
    when(pageCounter.count(any(Path.class))).thenReturn(1);
    PdfLinearizer linearizer = linearizer(commands, runner, pageCounter);

    byte[] result = linearizer.withLinearizedPdf(
      new ByteArrayInputStream("pdf-content".getBytes()), 9L,
      (path, pages) -> Files.readAllBytes(path));

    assertThat(result).isEqualTo("pdf-content".getBytes());
    try (var children = Files.list(tempDir)) {
      assertThat(children).isEmpty();
    }
  }

  @Test
  void rejectsFailedLinearizationAndDeletesTaskDirectory() throws Exception {
    ExternalProcessRunner runner = mock(ExternalProcessRunner.class);
    when(runner.run(any(), any())).thenReturn(new ProcessResult(2, "invalid pdf"));
    PdfPageCounter pageCounter = mock(PdfPageCounter.class);
    when(pageCounter.count(any(Path.class))).thenReturn(1);
    PdfLinearizer linearizer = linearizer(
      new QpdfCommandFactory("/usr/bin/qpdf"), runner, pageCounter);

    assertThatThrownBy(() -> linearizer.withLinearizedPdf(
      new ByteArrayInputStream("broken".getBytes()), 9L,
      (path, pages) -> Files.readAllBytes(path)))
      .isInstanceOf(TranscodeFailureException.class)
      .extracting("failureCode").isEqualTo("TRANSCODE_ERROR");
    try (var children = Files.list(tempDir)) {
      assertThat(children).isEmpty();
    }
  }

  @Test
  void doesNotExposeProcessOutputInFailureReason() {
    ExternalProcessRunner runner = mock(ExternalProcessRunner.class);
    when(runner.run(any(), any())).thenReturn(
      new ProcessResult(2, "sensitive path: /tmp/sts-transcode/pdf-9/source.pdf"));
    PdfPageCounter pageCounter = mock(PdfPageCounter.class);
    when(pageCounter.count(any(Path.class))).thenReturn(1);
    PdfLinearizer linearizer = linearizer(
      new QpdfCommandFactory("/usr/bin/qpdf"), runner, pageCounter);

    assertThatThrownBy(() -> linearizer.withLinearizedPdf(
      new ByteArrayInputStream("broken".getBytes()), 9L,
      (path, pages) -> Files.readAllBytes(path)))
      .isInstanceOf(TranscodeFailureException.class)
      .hasMessage("PDF 线性化失败")
      .hasMessageNotContaining("/tmp/sts-transcode");
  }

  @Test
  void doesNotInvokeQpdfWhenPageTreeNormalizationFails() {
    ExternalProcessRunner runner = mock(ExternalProcessRunner.class);
    PdfPageTreeNormalizer normalizer = mock(PdfPageTreeNormalizer.class);
    PdfPageCounter pageCounter = mock(PdfPageCounter.class);
    when(pageCounter.count(any(Path.class))).thenReturn(1);
    org.mockito.Mockito.doThrow(
        new TranscodeFailureException("SOURCE_INVALID", "PDF 页面树规范化失败"))
      .when(normalizer).normalize(any(Path.class), any(Path.class));
    PdfLinearizer linearizer = new PdfLinearizer(
      new QpdfCommandFactory("/usr/bin/qpdf"), runner, pageCounter, normalizer,
      tempDir.toString(), 30);

    assertThatThrownBy(() -> linearizer.withLinearizedPdf(
      new ByteArrayInputStream("broken".getBytes()), 9L,
      (path, pages) -> Files.readAllBytes(path)))
      .isInstanceOf(TranscodeFailureException.class)
      .extracting("failureCode").isEqualTo("SOURCE_INVALID");
    verify(runner, never()).run(any(), any());
  }

  private PdfLinearizer linearizer(QpdfCommandFactory commands, ExternalProcessRunner runner,
                                   PdfPageCounter pageCounter) {
    PdfPageTreeNormalizer normalizer = mock(PdfPageTreeNormalizer.class);
    org.mockito.Mockito.doAnswer(invocation -> {
      Path source = invocation.getArgument(0);
      Path output = invocation.getArgument(1);
      Files.copy(source, output);
      return null;
    }).when(normalizer).normalize(any(Path.class), any(Path.class));
    when(normalizer.validate(any(Path.class), eq(1))).thenReturn(1);
    return new PdfLinearizer(
      commands, runner, pageCounter, normalizer, tempDir.toString(), 30);
  }
}

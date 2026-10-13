package com.sts.asset.transcode;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.Loader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class QpdfRealProcessTest {
  private static final String SAMPLE_NAME =
    "海康-Infovision+iWork-Safety+企业安全生产管理平台用户手册.pdf";

  @TempDir
  Path tempDir;

  @Test
  void linearizesRepositoryLargePdfWithoutChangingPageCount() throws Exception {
    Path executable = Path.of("/usr/bin/qpdf");
    Path sample = findSample();
    assumeTrue(Files.isExecutable(executable), "测试机未安装 /usr/bin/qpdf");
    assumeTrue(Files.isRegularFile(sample), "仓库大 PDF 样本不存在");
    PdfLinearizer linearizer = new PdfLinearizer(
      new QpdfCommandFactory(executable.toString()), new ExternalProcessRunner(),
      new PdfPageCounter(), tempDir.toString(), 180);
    Path finalPdf = tempDir.resolve("final.pdf");

    long outputSize;
    try (var input = Files.newInputStream(sample)) {
      outputSize = linearizer.withLinearizedPdf(input, 1L, (path, pageCount) -> {
        assertThat(pageCount).isEqualTo(648);
        assertThat(new ExternalProcessRunner().run(
          new QpdfCommandFactory(executable.toString()).checkLinearization(path),
          java.time.Duration.ofSeconds(30)).exitCode()).isZero();
        Files.copy(path, finalPdf);
        return Files.size(path);
      });
    }

    assertThat(outputSize).isPositive();
    verifyRangePerformance(sample.getParent().getParent(), finalPdf);
  }

  @Test
  void linearizesConfiguredRestrictedPdfWithoutChangingSourceProtection() throws Exception {
    Path executable = Path.of("/usr/bin/qpdf");
    Path sample = Path.of(System.getProperty("restrictedPdfSample", ""));
    int expectedPages = Integer.getInteger("restrictedPdfExpectedPages", -1);
    assumeTrue(Files.isExecutable(executable), "测试机未安装 /usr/bin/qpdf");
    assumeTrue(Files.isRegularFile(sample), "未配置权限受限 PDF 样本");
    assumeTrue(expectedPages > 0, "未配置权限受限 PDF 预期页数");
    long sourceSize = Files.size(sample);
    byte[] sourceHash = sha256(sample);
    PdfLinearizer linearizer = new PdfLinearizer(
      new QpdfCommandFactory(executable.toString()), new ExternalProcessRunner(),
      new PdfPageCounter(), tempDir.toString(), 180);

    try (var input = Files.newInputStream(sample)) {
      linearizer.withLinearizedPdf(input, 2L, (path, pageCount) -> {
        assertThat(pageCount).isEqualTo(expectedPages);
        assertThat(new ExternalProcessRunner().run(
          new QpdfCommandFactory(executable.toString()).check(path),
          Duration.ofSeconds(30)).exitCode()).isZero();
        assertThat(new ExternalProcessRunner().run(
          new QpdfCommandFactory(executable.toString()).checkLinearization(path),
          Duration.ofSeconds(30)).exitCode()).isZero();
        try (var preview = Loader.loadPDF(path.toFile())) {
          assertThat(preview.isEncrypted()).isFalse();
          assertThat(preview.getNumberOfPages()).isEqualTo(expectedPages);
        }
        return pageCount;
      });
    }

    assertThat(Files.size(sample)).isEqualTo(sourceSize);
    assertThat(sha256(sample)).isEqualTo(sourceHash);
    try (var source = Loader.loadPDF(sample.toFile())) {
      assertThat(source.isEncrypted()).isTrue();
      assertThat(source.getNumberOfPages()).isEqualTo(expectedPages);
    }
  }

  private static byte[] sha256(Path path) throws Exception {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    try (var input = Files.newInputStream(path)) {
      byte[] buffer = new byte[8192];
      int read;
      while ((read = input.read(buffer)) >= 0) {
        digest.update(buffer, 0, read);
      }
    }
    return digest.digest();
  }

  private static void verifyRangePerformance(Path repositoryRoot, Path pdf) throws Exception {
    String node = "node";
    Path diagnostic = repositoryRoot.resolve(
      "frontend/shared/tests/pdf-range-transport-diagnostic.mjs");
    ProcessResult rangeResult = new ExternalProcessRunner().run(
      java.util.List.of(node, diagnostic.toString(), pdf.toString(), "262144"),
      Duration.ofSeconds(60));
    assertThat(rangeResult.exitCode()).withFailMessage(rangeResult.output()).isZero();
    int jsonStart = rangeResult.output().indexOf('{');
    assertThat(jsonStart).withFailMessage(rangeResult.output()).isGreaterThanOrEqualTo(0);
    JsonNode metrics = new ObjectMapper().readTree(rangeResult.output().substring(jsonStart));
    // 与前端 PDF_RANGE_CHUNK_SIZE=256KB 对齐。
    assertThat(metrics.path("documentBytes").asLong()).isLessThanOrEqualTo(2_500_000);
    assertThat(metrics.path("firstPageRenderBytes").asLong()).isLessThanOrEqualTo(3_500_000);
    assertThat(metrics.path("requestCount").asInt()).isLessThanOrEqualTo(16);
  }

  private static Path findSample() {
    Path current = Path.of("").toAbsolutePath().normalize();
    while (current != null) {
      Path candidate = current.resolve("test").resolve(SAMPLE_NAME);
      if (Files.isRegularFile(candidate)) return candidate;
      current = current.getParent();
    }
    return Path.of(SAMPLE_NAME).toAbsolutePath();
  }
}

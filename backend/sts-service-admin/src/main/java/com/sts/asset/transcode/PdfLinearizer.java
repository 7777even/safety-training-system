package com.sts.asset.transcode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.UUID;

public class PdfLinearizer {
  private final QpdfCommandFactory commands;
  private final ExternalProcessRunner runner;
  private final PdfPageCounter pageCounter;
  private final PdfPageTreeNormalizer pageTreeNormalizer;
  private final Path workRoot;
  private final Duration timeout;

  public PdfLinearizer(QpdfCommandFactory commands, ExternalProcessRunner runner,
                       PdfPageCounter pageCounter, String workDir, int timeoutSeconds) {
    this(commands, runner, pageCounter, new PdfPageTreeNormalizer(), workDir, timeoutSeconds);
  }

  public PdfLinearizer(QpdfCommandFactory commands, ExternalProcessRunner runner,
                       PdfPageCounter pageCounter, PdfPageTreeNormalizer pageTreeNormalizer,
                       String workDir, int timeoutSeconds) {
    if (timeoutSeconds <= 0) throw new IllegalArgumentException("qpdf timeout must be positive");
    this.commands = commands;
    this.runner = runner;
    this.pageCounter = pageCounter;
    this.pageTreeNormalizer = pageTreeNormalizer;
    this.workRoot = Path.of(workDir).toAbsolutePath().normalize();
    this.timeout = Duration.ofSeconds(timeoutSeconds);
  }

  public <T> T withLinearizedPdf(InputStream input, Long jobId, PdfWork<T> work) {
    Path jobDir = workRoot.resolve("pdf-" + jobId + "-" + UUID.randomUUID()).normalize();
    if (!jobDir.startsWith(workRoot) || jobDir.equals(workRoot)) {
      throw new IllegalStateException("非法 PDF 工作目录");
    }
    try {
      Files.createDirectories(jobDir);
      Path source = jobDir.resolve("source.pdf");
      Path normalized = jobDir.resolve("normalized.pdf");
      Path output = jobDir.resolve("document.pdf");
      Files.copy(input, source);
      int sourcePages = pageCounter.count(source);
      pageTreeNormalizer.normalize(source, normalized);
      requireSuccess(runner.run(commands.linearize(normalized, output), timeout), "PDF 线性化失败");
      if (!Files.isRegularFile(output) || Files.size(output) == 0) {
        throw new TranscodeFailureException("TRANSCODE_ERROR", "PDF 线性化未生成有效文件");
      }
      requireSuccess(runner.run(commands.check(output), timeout), "PDF 完整性校验失败");
      requireSuccess(runner.run(commands.checkLinearization(output), timeout), "PDF 线性化校验失败");
      int outputPages = pageCounter.count(output);
      if (sourcePages != outputPages) {
        throw new TranscodeFailureException("TRANSCODE_ERROR", "PDF 线性化前后页数不一致");
      }
      pageTreeNormalizer.validate(output, sourcePages);
      return work.apply(output, outputPages);
    } catch (TranscodeFailureException e) {
      throw e;
    } catch (IOException e) {
      throw new TranscodeFailureException("STORAGE_ERROR", "PDF 临时文件处理失败");
    } finally {
      deleteJobDirectory(jobDir);
    }
  }

  private static void requireSuccess(ProcessResult result, String message) {
    if (result.exitCode() != 0) {
      throw new TranscodeFailureException("TRANSCODE_ERROR", message);
    }
  }

  private void deleteJobDirectory(Path jobDir) {
    if (!jobDir.startsWith(workRoot) || jobDir.equals(workRoot) || !Files.exists(jobDir)) return;
    try (var paths = Files.walk(jobDir)) {
      paths.sorted(Comparator.reverseOrder()).forEach(path -> {
        try {
          Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
      });
    } catch (IOException ignored) {
    }
  }

  @FunctionalInterface
  public interface PdfWork<T> {
    T apply(Path linearizedPdf, int pageCount) throws IOException;
  }
}

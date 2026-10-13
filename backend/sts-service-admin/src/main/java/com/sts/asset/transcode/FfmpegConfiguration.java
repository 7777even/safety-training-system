package com.sts.asset.transcode;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Files;
import java.nio.file.Path;

@Configuration
@EnableConfigurationProperties(TranscodeProperties.class)
public class FfmpegConfiguration {
  @Bean
  ExternalProcessRunner externalProcessRunner() {
    return new ExternalProcessRunner();
  }

  @Bean
  PdfPageCounter pdfPageCounter() {
    return new PdfPageCounter();
  }

  @Bean
  PdfPageTreeNormalizer pdfPageTreeNormalizer() {
    return new PdfPageTreeNormalizer();
  }

  @Bean
  @ConditionalOnExpression("'${sts.transcode.provider:stub}' == 'ffmpeg'"
    + " && '${sts.transcode.document-provider:disabled}' == 'onlyoffice'")
  QpdfCommandFactory qpdfCommandFactory(TranscodeProperties properties) {
    String qpdfPath = properties.getQpdfPath();
    Path executable = Path.of(qpdfPath);
    if (!executable.isAbsolute() || !Files.isExecutable(executable)) {
      throw new IllegalStateException("qpdf path must point to an executable absolute path");
    }
    return new QpdfCommandFactory(qpdfPath);
  }

  @Bean
  @ConditionalOnExpression("'${sts.transcode.provider:stub}' == 'ffmpeg'"
    + " && '${sts.transcode.document-provider:disabled}' == 'onlyoffice'")
  PdfLinearizer pdfLinearizer(
    QpdfCommandFactory commands, ExternalProcessRunner runner, PdfPageCounter pageCounter,
    PdfPageTreeNormalizer pageTreeNormalizer, TranscodeProperties properties) {
    return new PdfLinearizer(commands, runner, pageCounter, pageTreeNormalizer, properties.getWorkDir(),
                             properties.getPdfLinearizeTimeoutSeconds());
  }

  @Bean
  @ConditionalOnProperty(name = "sts.transcode.provider", havingValue = "ffmpeg")
  FfmpegCommandFactory ffmpegCommandFactory(TranscodeProperties properties) {
    return new FfmpegCommandFactory(properties.getFfmpegPath(), properties.getFfprobePath());
  }
}

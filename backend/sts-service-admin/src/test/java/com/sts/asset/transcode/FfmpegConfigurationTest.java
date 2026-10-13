package com.sts.asset.transcode;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class FfmpegConfigurationTest {

  private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
    .withUserConfiguration(FfmpegConfiguration.class);

  @Test
  void stubProviderDoesNotCreateOrValidateLinuxCommandFactories() {
    contextRunner
      .withPropertyValues(
        "sts.transcode.provider=stub",
        "sts.transcode.document-provider=onlyoffice",
        "sts.transcode.ffmpeg-path=ffmpeg",
        "sts.transcode.ffprobe-path=ffprobe",
        "sts.transcode.qpdf-path=/missing/qpdf")
      .run(context -> {
        assertThat(context).hasNotFailed();
        assertThat(context).doesNotHaveBean(FfmpegCommandFactory.class);
        assertThat(context).doesNotHaveBean(QpdfCommandFactory.class);
        assertThat(context).doesNotHaveBean(PdfLinearizer.class);
      });
  }

  @Test
  void stubProviderDoesNotCreateOnlyOfficeClient() {
    new ApplicationContextRunner()
      .withUserConfiguration(FfmpegConfiguration.class, OnlyOfficeConfiguration.class)
      .withBean(ObjectMapper.class, ObjectMapper::new)
      .withPropertyValues(
        "sts.transcode.provider=stub",
        "sts.transcode.document-provider=onlyoffice")
      .run(context -> {
        assertThat(context).hasNotFailed();
        assertThat(context).doesNotHaveBean(OnlyOfficeClient.class);
      });
  }

  @Test
  void ffmpegProviderStillRejectsRelativeExecutablePaths() {
    contextRunner
      .withPropertyValues(
        "sts.transcode.provider=ffmpeg",
        "sts.transcode.ffmpeg-path=ffmpeg",
        "sts.transcode.ffprobe-path=ffprobe")
      .run(context -> assertThat(context).hasFailed());
  }

  @Test
  void onlyOfficeProviderRejectsMissingQpdfExecutable() {
    contextRunner
      .withPropertyValues(
        "sts.transcode.provider=ffmpeg",
        "sts.transcode.document-provider=onlyoffice",
        "sts.transcode.qpdf-path=/missing/qpdf")
      .run(context -> assertThat(context).hasFailed());
  }
}

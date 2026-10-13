package com.sts.asset.transcode;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class FfmpegCommandFactoryTest {
  @Test
  void videoCommandUsesSafeArgumentListAndRequiredHlsProfile() {
    FfmpegCommandFactory factory = new FfmpegCommandFactory("/usr/bin/ffmpeg", "/usr/bin/ffprobe");

    var command = factory.video(Path.of("/tmp/source name.mp4"), Path.of("/tmp/job"));

    assertThat(command).startsWith("/usr/bin/ffmpeg", "-nostdin", "-y", "-i", "/tmp/source name.mp4");
    assertThat(command).contains("-vf", "scale=-2:min(720\\,ih)", "-c:v", "libx264",
                                 "-flags", "+cgop", "-force_key_frames", "expr:gte(t,n_forced*5)",
                                 "-c:a", "aac", "-hls_time", "5", "-hls_flags", "independent_segments",
                                 "-hls_playlist_type", "vod");
  }

  @Test
  void audioCommandProducesAacHlsWithIndependentSegments() {
    FfmpegCommandFactory factory = new FfmpegCommandFactory("/usr/bin/ffmpeg", "/usr/bin/ffprobe");
    Path outputDirectory = Path.of("/tmp/job");

    var command = factory.audio(Path.of("/tmp/source name.wav"), outputDirectory);

    assertThat(command).startsWith("/usr/bin/ffmpeg", "-nostdin", "-y", "-i", "/tmp/source name.wav");
    assertThat(command).contains("-vn", "-c:a", "aac", "-b:a", "128k", "-f", "hls", "-hls_time", "5",
                                 "-hls_flags", "independent_segments", "-hls_playlist_type", "vod");
    assertThat(command).doesNotContain("libmp3lame");
    assertThat(command).contains(outputDirectory.resolve("segment-%05d.ts").toString(),
                                 outputDirectory.resolve("index.m3u8").toString());
  }
}

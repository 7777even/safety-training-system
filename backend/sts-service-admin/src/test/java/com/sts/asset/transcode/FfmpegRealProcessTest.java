package com.sts.asset.transcode;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class FfmpegRealProcessTest {
  @TempDir
  Path tempDir;
  private final ExternalProcessRunner runner = new ExternalProcessRunner();
  private final FfmpegCommandFactory commands =
    new FfmpegCommandFactory("/usr/bin/ffmpeg", "/usr/bin/ffprobe");

  @Test
  void realFfmpegProducesPlayableVideoArtifactsAndAccurateDuration() throws Exception {
    assumeFfmpegInstalled();
    Path source = tempDir.resolve("source.mp4");
    assertThat(runner.run(List.of("/usr/bin/ffmpeg", "-nostdin", "-y",
                                  "-f", "lavfi", "-i", "color=c=blue:s=640x360:d=2",
                                  "-f", "lavfi", "-i", "sine=frequency=1000:duration=2",
                                  "-c:v", "libx264", "-c:a", "aac", "-shortest", source.toString()),
                          Duration.ofSeconds(20)).exitCode()).isZero();

    Path hls = Files.createDirectory(tempDir.resolve("hls"));
    assertThat(runner.run(commands.video(source, hls), Duration.ofSeconds(20)).exitCode()).isZero();
    assertThat(runner.run(commands.thumbnail(source, tempDir.resolve("cover.jpg")),
                          Duration.ofSeconds(20)).exitCode()).isZero();

    assertThat(hls.resolve("index.m3u8")).exists();
    try (var paths = Files.list(hls)) {
      assertThat(paths.anyMatch(path -> path.toString().endsWith(".ts"))).isTrue();
    }
    assertThat(tempDir.resolve("cover.jpg")).exists();
    double duration = Double.parseDouble(
      runner.run(commands.probeDuration(source), Duration.ofSeconds(5)).output().trim());
    assertThat(duration).isBetween(1.0, 3.0);
  }

  @Test
  void realFfmpegProducesAudioHlsManifestAndSegment() throws Exception {
    assumeFfmpegInstalled();
    Path source = tempDir.resolve("source.wav");
    assertThat(runner.run(List.of("/usr/bin/ffmpeg", "-nostdin", "-y", "-f", "lavfi",
                                  "-i", "sine=frequency=440:duration=6", source.toString()),
                          Duration.ofSeconds(10)).exitCode())
      .isZero();
    Path hls = Files.createDirectory(tempDir.resolve("audio-hls"));
    assertThat(runner.run(commands.audio(source, hls), Duration.ofSeconds(20)).exitCode()).isZero();
    assertThat(hls.resolve("index.m3u8")).exists().isNotEmptyFile();
    try (var paths = Files.list(hls)) {
      assertThat(paths.anyMatch(path -> path.toString().endsWith(".ts"))).isTrue();
    }
    assertThat(hls.resolve("audio.mp3")).doesNotExist();
  }

  private static void assumeFfmpegInstalled() {
    assumeTrue(Files.isExecutable(Path.of("/usr/bin/ffmpeg")), "测试机未安装 /usr/bin/ffmpeg");
    assumeTrue(Files.isExecutable(Path.of("/usr/bin/ffprobe")), "测试机未安装 /usr/bin/ffprobe");
  }
}

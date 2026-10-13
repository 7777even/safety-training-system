package com.sts.asset.transcode;

import java.nio.file.Path;
import java.util.List;

public class FfmpegCommandFactory {
  private final String ffmpegPath;
  private final String ffprobePath;

  public FfmpegCommandFactory(String ffmpegPath, String ffprobePath) {
    requireAbsolute(ffmpegPath, "ffmpeg");
    requireAbsolute(ffprobePath, "ffprobe");
    this.ffmpegPath = ffmpegPath;
    this.ffprobePath = ffprobePath;
  }

  public List<String> probeDuration(Path source) {
    return List.of(ffprobePath, "-v", "error", "-show_entries", "format=duration",
                   "-of", "default=noprint_wrappers=1:nokey=1", source.toString());
  }

  public List<String> video(Path source, Path outputDirectory) {
    return List.of(ffmpegPath, "-nostdin", "-y", "-i", source.toString(),
                   "-vf", "scale=-2:min(720\\,ih)", "-c:v", "libx264", "-preset", "medium",
                   "-flags", "+cgop", "-force_key_frames", "expr:gte(t,n_forced*5)",
                   "-c:a", "aac", "-b:a", "128k", "-f", "hls", "-hls_time", "5",
                   "-hls_flags", "independent_segments", "-hls_playlist_type", "vod",
                   "-hls_segment_filename",
                   outputDirectory.resolve("segment-%05d.ts").toString(),
                   outputDirectory.resolve("index.m3u8").toString());
  }

  public List<String> thumbnail(Path source, Path output) {
    return List.of(ffmpegPath, "-nostdin", "-y", "-ss", "1", "-i", source.toString(),
                   "-frames:v", "1", "-vf", "scale=-2:480", output.toString());
  }

  /**
   * 音频 HLS：无视频轨、AAC 128k、5 秒独立切片，产物为 m3u8 + ts。
   */
  public List<String> audio(Path source, Path outputDirectory) {
    return List.of(ffmpegPath, "-nostdin", "-y", "-i", source.toString(), "-vn",
                   "-c:a", "aac", "-b:a", "128k", "-f", "hls", "-hls_time", "5",
                   "-hls_flags", "independent_segments", "-hls_playlist_type", "vod",
                   "-hls_segment_filename",
                   outputDirectory.resolve("segment-%05d.ts").toString(),
                   outputDirectory.resolve("index.m3u8").toString());
  }

  private static void requireAbsolute(String value, String name) {
    if (value == null || !Path.of(value).isAbsolute()) {
      throw new IllegalArgumentException(name + " path must be absolute");
    }
  }
}

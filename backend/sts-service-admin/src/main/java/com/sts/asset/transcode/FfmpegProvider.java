package com.sts.asset.transcode;

import com.sts.admin.entity.SysFileInfo;
import com.sts.admin.mapper.SysFileInfoMapper;
import com.sts.admin.storage.S3StorageGateway;
import com.sts.admin.storage.StorageObject;
import com.sts.admin.storage.TenantObjectKeyService;
import com.sts.asset.entity.Asset;
import com.sts.asset.entity.AssetTranscodeJob;
import com.sts.asset.mapper.AssetMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "sts.transcode.provider", havingValue = "ffmpeg")
public class FfmpegProvider implements TranscodeProvider {
  private final AssetMapper assetMapper;
  private final SysFileInfoMapper fileInfoMapper;
  private final S3StorageGateway storage;
  private final FfmpegCommandFactory commands;
  private final ExternalProcessRunner runner;
  private final TranscodeProperties properties;
  private final TenantObjectKeyService objectKeys;

  @Override
  public boolean supports(String jobType) {
    return "VIDEO_TRANSCODE".equals(jobType) || "AUDIO_TRANSCODE".equals(jobType);
  }

  @Override
  public TranscodeOutput execute(AssetTranscodeJob job) {
    Asset asset = assetMapper.selectById(job.getAssetId());
    if (asset == null || asset.getFileInfoId() == null) {
      throw new TranscodeFailureException("SOURCE_INVALID", "素材源文件不存在");
    }
    SysFileInfo sourceInfo = fileInfoMapper.selectById(asset.getFileInfoId());
    if (sourceInfo == null || sourceInfo.getObjectName() == null) {
      throw new TranscodeFailureException("SOURCE_INVALID", "素材源文件记录不存在");
    }

    Path root = Path.of(properties.getWorkDir()).toAbsolutePath().normalize();
    Path jobDir = root.resolve(job.getTranscodeJobId() + "-" + UUID.randomUUID()).normalize();
    if (!jobDir.startsWith(root)) throw new IllegalStateException("非法转码工作目录");
    List<String> uploaded = new ArrayList<>();
    try {
      Files.createDirectories(jobDir);
      Path source = jobDir.resolve("source." + safeSuffix(sourceInfo.getFileSuffix()));
      download(sourceInfo.getObjectName(), source);
      int duration = probeDuration(source);
      List<TranscodeArtifact> artifacts = "VIDEO_TRANSCODE".equals(job.getJobType())
        ? transcodeVideo(job, source, jobDir, uploaded)
        : transcodeAudio(job, source, jobDir, uploaded);
      return new TranscodeOutput(duration, artifacts, List.copyOf(uploaded));
    } catch (IOException e) {
      cleanupUploaded(uploaded);
      throw new TranscodeFailureException("STORAGE_ERROR", "转码临时文件处理失败");
    } catch (RuntimeException e) {
      cleanupUploaded(uploaded);
      throw e;
    } finally {
      deleteJobDirectory(jobDir, root);
    }
  }

  @Override
  public void cleanup(TranscodeOutput output) {
    if (output != null) cleanupUploaded(output.uploadedObjectNames());
  }

  private int probeDuration(Path source) {
    ProcessResult result = run(commands.probeDuration(source));
    try {
      return Math.max(0, (int) Math.round(Double.parseDouble(result.output().trim())));
    } catch (NumberFormatException e) {
      throw new TranscodeFailureException("SOURCE_INVALID", "无法读取媒体时长");
    }
  }

  private List<TranscodeArtifact> transcodeVideo(AssetTranscodeJob job, Path source,
                                                 Path jobDir, List<String> uploaded) throws IOException {
    Path hls = Files.createDirectory(jobDir.resolve("hls"));
    requireSuccess(run(commands.video(source, hls)));
    Path thumbnail = jobDir.resolve("thumbnail.jpg");
    requireSuccess(run(commands.thumbnail(source, thumbnail)));
    String base = prefix(job, "hls");
    try (var paths = Files.list(hls)) {
      for (Path path : paths.sorted().toList()) {
        upload(path, base + "/" + path.getFileName(), contentType(path), uploaded);
      }
    }
    String thumbnailKey = prefix(job, "thumbnail") + "/cover.jpg";
    upload(thumbnail, thumbnailKey, "image/jpeg", uploaded);
    return List.of(
      artifact("HLS", base + "/index.m3u8", "index.m3u8", Files.size(hls.resolve("index.m3u8"))),
      artifact("THUMBNAIL", thumbnailKey, "cover.jpg", Files.size(thumbnail)));
  }

  /**
   * 音频只产出 HLS 切片，不生成封面，也不再写 TRANSCODED MP3。
   */
  private List<TranscodeArtifact> transcodeAudio(AssetTranscodeJob job, Path source,
                                                 Path jobDir, List<String> uploaded) throws IOException {
    Path hls = Files.createDirectory(jobDir.resolve("hls"));
    requireSuccess(run(commands.audio(source, hls)));
    String base = prefix(job, "hls");
    try (var paths = Files.list(hls)) {
      for (Path path : paths.sorted().toList()) {
        upload(path, base + "/" + path.getFileName(), contentType(path), uploaded);
      }
    }
    return List.of(
      artifact("HLS", base + "/index.m3u8", "index.m3u8", Files.size(hls.resolve("index.m3u8"))));
  }

  private ProcessResult run(List<String> command) {
    return runner.run(command, Duration.ofMinutes(properties.getProcessTimeoutMinutes()));
  }

  private static void requireSuccess(ProcessResult result) {
    if (result.exitCode() == 0) return;
    String output = result.output().toLowerCase(Locale.ROOT);
    String code = output.contains("invalid data") ? "SOURCE_INVALID"
      : output.contains("unknown decoder") || output.contains("unsupported")
      ? "UNSUPPORTED_CODEC" : "TRANSCODE_ERROR";
    throw new TranscodeFailureException(code, "FFmpeg 执行失败: " + result.output());
  }

  private void download(String objectName, Path target) throws IOException {
    try {
      StorageObject object = storage.getObject(objectName);
      try (var input = object.inputStream()) {
        Files.copy(input, target);
      }
    } catch (RuntimeException e) {
      throw new TranscodeFailureException("STORAGE_ERROR", "读取源文件失败");
    }
  }

  private void upload(Path path, String objectName, String contentType,
                      List<String> uploaded) throws IOException {
    try {
      try (var input = Files.newInputStream(path)) {
        storage.putObject(objectName, input, Files.size(path), contentType);
        uploaded.add(objectName);
      }
    } catch (RuntimeException e) {
      throw new TranscodeFailureException("STORAGE_ERROR", "上传派生文件失败");
    }
  }

  private void cleanupUploaded(List<String> uploaded) {
    uploaded.forEach(key -> {
      try {
        storage.deleteObject(key);
      } catch (RuntimeException ignored) {
      }
    });
  }

  private static void deleteJobDirectory(Path jobDir, Path root) {
    if (!jobDir.startsWith(root) || jobDir.equals(root) || !Files.exists(jobDir)) return;
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

  String prefix(AssetTranscodeJob job, String type) {
    return objectKeys.forCurrentTenant(
      "derivative/" + job.getAssetId() + "/" + job.getTranscodeJobId() + "/" + type);
  }

  private static TranscodeArtifact artifact(String type, String key, String name, long size) {
    return new TranscodeArtifact(type, key, name, size, safeSuffix(name));
  }

  private static String safeSuffix(String suffix) {
    String value = suffix == null ? "bin" : suffix.toLowerCase(Locale.ROOT).replace(".", "");
    return value.matches("[a-z0-9]{1,10}") ? value : "bin";
  }

  private static String contentType(Path path) {
    String name = path.getFileName().toString();
    return name.endsWith(".m3u8") ? "application/vnd.apple.mpegurl" : "video/mp2t";
  }
}

package com.sts.asset.transcode;

import com.sts.admin.entity.SysFileInfo;
import com.sts.admin.mapper.SysFileInfoMapper;
import com.sts.admin.storage.S3StorageGateway;
import com.sts.admin.storage.StorageObject;
import com.sts.admin.storage.TenantObjectKeyService;
import com.sts.asset.entity.Asset;
import com.sts.asset.entity.AssetTranscodeJob;
import com.sts.asset.mapper.AssetMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FfmpegProviderAudioHlsTest {
  @TempDir
  Path tempDir;

  @Test
  void audioExecuteUploadsHlsPrefixAndDoesNotEmitMp3() throws Exception {
    AssetMapper assetMapper = mock(AssetMapper.class);
    SysFileInfoMapper fileInfoMapper = mock(SysFileInfoMapper.class);
    S3StorageGateway storage = mock(S3StorageGateway.class);
    TenantObjectKeyService objectKeys = mock(TenantObjectKeyService.class);
    ExternalProcessRunner runner = mock(ExternalProcessRunner.class);
    when(objectKeys.forCurrentTenant(anyString()))
      .thenAnswer(invocation -> "ACME/" + invocation.getArgument(0));

    Asset asset = new Asset();
    asset.setAssetId(20L);
    asset.setFileInfoId(7L);
    when(assetMapper.selectById(20L)).thenReturn(asset);
    SysFileInfo source = new SysFileInfo();
    source.setObjectName("source/audio.wav");
    source.setFileSuffix("wav");
    when(fileInfoMapper.selectById(7L)).thenReturn(source);
    when(storage.getObject("source/audio.wav"))
      .thenReturn(new StorageObject(new ByteArrayInputStream(new byte[]{1, 2, 3}), 3, "audio/wav"));

    List<String> uploadedKeys = new ArrayList<>();
    doAnswer(invocation -> {
      uploadedKeys.add(invocation.getArgument(0));
      return null;
    }).when(storage).putObject(anyString(), any(), anyLong(), anyString());
    when(runner.run(any(), any(Duration.class))).thenAnswer(invocation -> {
      List<String> command = invocation.getArgument(0);
      if (command.contains("-show_entries")) {
        return new ProcessResult(0, "4.2");
      }
      Path playlist = Path.of(command.getLast());
      Files.createDirectories(playlist.getParent());
      Files.writeString(playlist, "#EXTM3U\n");
      Files.write(playlist.resolveSibling("segment-00000.ts"), new byte[]{9, 8, 7});
      return new ProcessResult(0, "");
    });

    TranscodeProperties properties = new TranscodeProperties();
    properties.setWorkDir(tempDir.toString());
    FfmpegProvider provider = new FfmpegProvider(
      assetMapper, fileInfoMapper, storage,
      new FfmpegCommandFactory("/usr/bin/ffmpeg", "/usr/bin/ffprobe"),
      runner, properties, objectKeys);
    AssetTranscodeJob job = new AssetTranscodeJob();
    job.setAssetId(20L);
    job.setTranscodeJobId(9L);
    job.setJobType("AUDIO_TRANSCODE");

    TranscodeOutput output = provider.execute(job);

    assertThat(output.artifacts()).extracting(TranscodeArtifact::derivativeType).containsExactly("HLS");
    assertThat(output.artifacts()).extracting(TranscodeArtifact::objectName)
      .containsExactly("ACME/derivative/20/9/hls/index.m3u8");
    assertThat(output.artifacts()).extracting(TranscodeArtifact::fileName).containsExactly("index.m3u8");
    assertThat(uploadedKeys).containsExactly(
      "ACME/derivative/20/9/hls/index.m3u8",
      "ACME/derivative/20/9/hls/segment-00000.ts");
    assertThat(uploadedKeys).noneMatch(key -> key.contains("audio.mp3") || key.contains("/transcoded/"));
    assertThat(output.artifacts()).noneMatch(item -> "TRANSCODED".equals(item.derivativeType())
      || "THUMBNAIL".equals(item.derivativeType()));
  }
}

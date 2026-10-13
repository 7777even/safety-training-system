package com.sts.asset.transcode;

import com.sts.admin.entity.SysFileInfo;
import com.sts.admin.mapper.SysFileInfoMapper;
import com.sts.asset.entity.AssetDerivative;
import com.sts.asset.entity.AssetTranscodeJob;
import com.sts.asset.mapper.AssetDerivativeMapper;
import com.sts.asset.mapper.AssetMapper;
import com.sts.asset.mapper.AssetTranscodeJobMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TranscodeResultServiceTest {
  private AssetTranscodeJobMapper jobMapper;
  private AssetMapper assetMapper;
  private AssetDerivativeMapper derivativeMapper;
  private SysFileInfoMapper fileInfoMapper;
  private TranscodeResultService resultService;

  @BeforeEach
  void setUp() {
    jobMapper = mock(AssetTranscodeJobMapper.class);
    assetMapper = mock(AssetMapper.class);
    derivativeMapper = mock(AssetDerivativeMapper.class);
    fileInfoMapper = mock(SysFileInfoMapper.class);
    resultService = new TranscodeResultService(jobMapper, assetMapper, derivativeMapper, fileInfoMapper);
  }

  @Test
  void rejectsVideoSuccessWithoutAnyArtifacts() {
    AssetTranscodeJob job = videoJob();

    assertThatThrownBy(() -> resultService.succeed(job, TranscodeOutput.empty()))
      .isInstanceOf(TranscodeFailureException.class)
      .hasMessage("视频转码缺少派生产物: HLS, THUMBNAIL")
      .extracting("failureCode")
      .isEqualTo("TRANSCODE_ERROR");

    verifyNoInteractions(fileInfoMapper, derivativeMapper, assetMapper);
    verify(jobMapper, never()).finishSucceeded(anyLong());
  }

  @Test
  void rejectsVideoSuccessWhenThumbnailIsMissing() {
    AssetTranscodeJob job = videoJob();
    TranscodeOutput output = outputWith(artifact("HLS", "hls/index.m3u8", "index.m3u8"));

    assertThatThrownBy(() -> resultService.succeed(job, output))
      .isInstanceOf(TranscodeFailureException.class)
      .hasMessage("视频转码缺少派生产物: THUMBNAIL");

    verifyNoInteractions(fileInfoMapper, derivativeMapper, assetMapper);
    verify(jobMapper, never()).finishSucceeded(anyLong());
  }

  @Test
  void rejectsVideoSuccessWhenHlsIsMissing() {
    AssetTranscodeJob job = videoJob();
    TranscodeOutput output = outputWith(artifact("THUMBNAIL", "thumbnail/cover.jpg", "cover.jpg"));

    assertThatThrownBy(() -> resultService.succeed(job, output))
      .isInstanceOf(TranscodeFailureException.class)
      .hasMessage("视频转码缺少派生产物: HLS");

    verifyNoInteractions(fileInfoMapper, derivativeMapper, assetMapper);
    verify(jobMapper, never()).finishSucceeded(anyLong());
  }

  @Test
  void commitsVideoSuccessWhenHlsAndThumbnailArePresent() {
    AssetTranscodeJob job = videoJob();
    TranscodeOutput output = outputWith(
      artifact("HLS", "hls/index.m3u8", "index.m3u8"),
      artifact("THUMBNAIL", "thumbnail/cover.jpg", "cover.jpg"));
    when(jobMapper.finishSucceeded(9L)).thenReturn(1);

    resultService.succeed(job, output);

    verify(fileInfoMapper, times(2)).insert(any(SysFileInfo.class));
    verify(derivativeMapper, times(2)).insert(any(AssetDerivative.class));
    verify(jobMapper).finishSucceeded(9L);
    verify(assetMapper).finishTranscode(20L, 1L, 153, null);
  }

  @Test
  void rejectsAudioSuccessWithoutAnyArtifacts() {
    AssetTranscodeJob job = audioJob();

    assertThatThrownBy(() -> resultService.succeed(job, TranscodeOutput.empty()))
      .isInstanceOf(TranscodeFailureException.class)
      .hasMessage("音频转码缺少派生产物: HLS")
      .extracting("failureCode")
      .isEqualTo("TRANSCODE_ERROR");

    verifyNoInteractions(fileInfoMapper, derivativeMapper, assetMapper);
    verify(jobMapper, never()).finishSucceeded(anyLong());
  }

  @Test
  void rejectsAudioSuccessWhenHlsIsMissing() {
    AssetTranscodeJob job = audioJob();
    TranscodeOutput output = outputWith(artifact("TRANSCODED", "transcoded/audio.mp3", "audio.mp3"));

    assertThatThrownBy(() -> resultService.succeed(job, output))
      .isInstanceOf(TranscodeFailureException.class)
      .hasMessage("音频转码缺少派生产物: HLS")
      .extracting("failureCode")
      .isEqualTo("TRANSCODE_ERROR");

    verifyNoInteractions(fileInfoMapper, derivativeMapper, assetMapper);
    verify(jobMapper, never()).finishSucceeded(anyLong());
  }

  @Test
  void commitsAudioSuccessWhenHlsIsPresentWithoutThumbnail() {
    AssetTranscodeJob job = audioJob();
    TranscodeOutput output = outputWith(artifact("HLS", "hls/index.m3u8", "index.m3u8"));
    when(jobMapper.finishSucceeded(9L)).thenReturn(1);

    resultService.succeed(job, output);

    verify(fileInfoMapper).insert(any(SysFileInfo.class));
    verify(derivativeMapper).insert(any(AssetDerivative.class));
    verify(jobMapper).finishSucceeded(9L);
    verify(assetMapper).finishTranscode(20L, 1L, 153, null);
  }

  @Test
  void lateTimeoutCannotOverwriteAssetAfterJobAlreadySucceeded() {
    AssetTranscodeJob job = videoJob();
    when(jobMapper.finishFailed(9L, "TIMEOUT", "超时")).thenReturn(0);

    resultService.fail(job, "TIMEOUT", "超时");

    verifyNoInteractions(assetMapper);
  }

  @Test
  void wonFailureTransitionUpdatesAssetToFailed() {
    AssetTranscodeJob job = videoJob();
    when(jobMapper.finishFailed(9L, "TIMEOUT", "超时")).thenReturn(1);

    resultService.fail(job, "TIMEOUT", "超时");

    verify(assetMapper).updateProcessStatus(20L, 1L, "FAILED");
  }

  private static AssetTranscodeJob videoJob() {
    return job("VIDEO_TRANSCODE");
  }

  private static AssetTranscodeJob audioJob() {
    return job("AUDIO_TRANSCODE");
  }

  private static AssetTranscodeJob job(String jobType) {
    AssetTranscodeJob job = new AssetTranscodeJob();
    job.setTranscodeJobId(9L);
    job.setOrganizationId(1L);
    job.setAssetId(20L);
    job.setJobType(jobType);
    job.setJobStatus("RUNNING");
    return job;
  }

  private static TranscodeOutput outputWith(TranscodeArtifact... artifacts) {
    return new TranscodeOutput(153, List.of(artifacts),
                               List.of("derivative/20/9/output"));
  }

  private static TranscodeArtifact artifact(String type, String objectName, String fileName) {
    String suffix = fileName.substring(fileName.lastIndexOf('.') + 1);
    return new TranscodeArtifact(type, objectName, fileName, 1024, suffix);
  }
}

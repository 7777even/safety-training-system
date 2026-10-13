package com.sts.asset.transcode;

import com.sts.asset.entity.AssetTranscodeJob;
import com.sts.asset.mapper.AssetTranscodeJobMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class TranscodeJobProcessorTest {
  private AssetTranscodeJobMapper mapper;
  private TranscodeProviderRegistry registry;
  private TranscodeResultService resultService;
  private TranscodeJobProcessor processor;

  @BeforeEach
  void setUp() {
    mapper = mock(AssetTranscodeJobMapper.class);
    registry = mock(TranscodeProviderRegistry.class);
    resultService = mock(TranscodeResultService.class);
    processor = new TranscodeJobProcessor(mapper, registry, resultService);
  }

  @Test
  void onlyTheInstanceThatClaimsPendingJobExecutesProvider() {
    when(mapper.claimPending(9L)).thenReturn(0);

    processor.process(9L);

    verifyNoInteractions(registry, resultService);
  }

  @Test
  void stubProviderFailsExplicitlyInsteadOfReturningFalseSuccess() {
    AssetTranscodeJob job = job();
    TranscodeProvider provider = new StubTranscodeProvider();
    when(mapper.claimPending(9L)).thenReturn(1);
    when(mapper.selectById(9L)).thenReturn(job);
    when(registry.require("VIDEO_TRANSCODE")).thenReturn(provider);

    processor.process(9L);

    verify(resultService).markProcessing(job);
    verify(resultService, never()).succeed(any(), any());
    verify(resultService).fail(job, "PROVIDER_DISABLED", "当前环境未启用真实转码服务，任务未执行");
  }

  @Test
  void providerFailureIsClassifiedAndSensitiveReasonIsSanitized() {
    AssetTranscodeJob job = job();
    TranscodeProvider provider = mock(TranscodeProvider.class);
    when(mapper.claimPending(9L)).thenReturn(1);
    when(mapper.selectById(9L)).thenReturn(job);
    when(registry.require("VIDEO_TRANSCODE")).thenReturn(provider);
    doThrow(new TranscodeFailureException("STORAGE_ERROR",
                                          "token=secret-value https://minio.local/object?X-Amz-Signature=signature endpoint failed"))
      .when(provider).execute(job);

    processor.process(9L);

    verify(resultService).fail(eq(job), eq("STORAGE_ERROR"),
                               argThat(reason -> !reason.contains("secret-value")
                                 && !reason.contains("signature") && reason.length() <= 1000));
  }

  @Test
  void databaseCommitFailureCleansUploadedArtifactsBeforeFailingJob() {
    AssetTranscodeJob job = job();
    TranscodeProvider provider = mock(TranscodeProvider.class);
    TranscodeOutput output = new TranscodeOutput(5, java.util.List.of(),
                                                 java.util.List.of("derivative/20/9/hls/index.m3u8"));
    when(mapper.claimPending(9L)).thenReturn(1);
    when(mapper.selectById(9L)).thenReturn(job);
    when(registry.require("VIDEO_TRANSCODE")).thenReturn(provider);
    when(provider.execute(job)).thenReturn(output);
    doThrow(new IllegalStateException("database unavailable"))
      .when(resultService).succeed(job, output);

    processor.process(9L);

    verify(provider).cleanup(output);
    verify(resultService).fail(job, "TRANSCODE_ERROR", "database unavailable");
  }

  @Test
  void diagnosticThrowableKeepsStackAndTypeWithoutSensitiveValues() {
    IllegalStateException cause = new IllegalStateException(
      "password=reader-secret Authorization: Bearer auth-secret "
        + "https://minio.local/object?X-Amz-Signature=signature");
    TranscodeFailureException failure = new TranscodeFailureException(
      "SOURCE_INVALID", "PDF 页面树规范化失败", cause);

    Throwable diagnostic = TranscodeJobProcessor.diagnostic(failure);

    assertThat(diagnostic.getMessage())
      .contains(TranscodeFailureException.class.getName())
      .doesNotContain("reader-secret", "auth-secret", "signature");
    assertThat(diagnostic.getStackTrace()).containsExactly(failure.getStackTrace());
    assertThat(diagnostic.getCause()).isNotNull();
    assertThat(diagnostic.getCause().getMessage())
      .contains(IllegalStateException.class.getName())
      .doesNotContain("reader-secret", "auth-secret", "signature");
  }

  private static AssetTranscodeJob job() {
    AssetTranscodeJob job = new AssetTranscodeJob();
    job.setTranscodeJobId(9L);
    // 任务必须携带租户：process 会据此绑定上下文，该列在库中为 NOT NULL
    job.setOrganizationId(1L);
    job.setAssetId(20L);
    job.setJobType("VIDEO_TRANSCODE");
    job.setJobStatus("RUNNING");
    return job;
  }
}

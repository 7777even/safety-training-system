package com.sts.asset.transcode;

import com.sts.asset.entity.AssetTranscodeJob;
import com.sts.asset.mapper.AssetTranscodeJobMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TranscodeTimeoutRecoveryTest {
  @Test
  void runningJobsOlderThanThresholdBecomeTimeoutFailures() {
    AssetTranscodeJobMapper mapper = mock(AssetTranscodeJobMapper.class);
    TranscodeResultService resultService = mock(TranscodeResultService.class);
    AssetTranscodeJob job = new AssetTranscodeJob();
    job.setTranscodeJobId(9L);
    when(mapper.selectTimedOutRunning(any(LocalDateTime.class), eq(20))).thenReturn(List.of(job));
    TranscodeProperties properties = new TranscodeProperties();
    properties.setRunningTimeoutMinutes(30);
    TranscodeTimeoutRecovery recovery = new TranscodeTimeoutRecovery(mapper, resultService, properties);

    recovery.recover();

    verify(resultService).fail(job, "TIMEOUT", "转码任务运行超过 30 分钟");
  }
}

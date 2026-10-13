package com.sts.asset.transcode;

import com.sts.asset.mapper.AssetTranscodeJobMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.RejectedExecutionException;

@Component
public class TranscodeScheduler {
  private final AssetTranscodeJobMapper mapper;
  private final TranscodeJobProcessor processor;
  private final TranscodeTimeoutRecovery timeoutRecovery;
  private final TaskExecutor executor;
  private final int concurrency;

  public TranscodeScheduler(AssetTranscodeJobMapper mapper,
                            TranscodeJobProcessor processor,
                            TranscodeTimeoutRecovery timeoutRecovery,
                            @Qualifier("transcodeTaskExecutor") TaskExecutor executor,
                            TranscodeProperties properties) {
    this.mapper = mapper;
    this.processor = processor;
    this.timeoutRecovery = timeoutRecovery;
    this.executor = executor;
    this.concurrency = properties.getConcurrency();
  }

  @Scheduled(fixedDelayString = "${sts.transcode.poll-interval-ms:3000}")
  public void poll() {
    for (Long jobId : mapper.selectPendingIds(concurrency)) {
      try {
        executor.execute(() -> processor.process(jobId));
      } catch (RejectedExecutionException ignored) {
        break;
      }
    }
  }

  @Scheduled(fixedDelayString = "${sts.transcode.timeout-scan-interval-ms:60000}")
  public void recoverTimeouts() {
    timeoutRecovery.recover();
  }
}

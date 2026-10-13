package com.sts.asset.transcode;

import com.sts.asset.entity.AssetTranscodeJob;
import com.sts.asset.mapper.AssetTranscodeJobMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class TranscodeTimeoutRecovery {
  private final AssetTranscodeJobMapper mapper;
  private final TranscodeResultService resultService;
  private final int timeoutMinutes;

  public TranscodeTimeoutRecovery(AssetTranscodeJobMapper mapper,
                                  TranscodeResultService resultService,
                                  TranscodeProperties properties) {
    this.mapper = mapper;
    this.resultService = resultService;
    this.timeoutMinutes = properties.getRunningTimeoutMinutes();
  }

  public int recover() {
    List<AssetTranscodeJob> jobs = mapper.selectTimedOutRunning(
      LocalDateTime.now().minusMinutes(timeoutMinutes), 20);
    jobs.forEach(job -> resultService.fail(
      job, "TIMEOUT", "转码任务运行超过 " + timeoutMinutes + " 分钟"));
    return jobs.size();
  }
}

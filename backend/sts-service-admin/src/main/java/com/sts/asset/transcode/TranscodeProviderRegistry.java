package com.sts.asset.transcode;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class TranscodeProviderRegistry {
  private final List<TranscodeProvider> providers;

  public TranscodeProvider require(String jobType) {
    return providers.stream().filter(provider -> provider.supports(jobType)).findFirst()
      .orElseThrow(() -> new TranscodeFailureException(
        "TRANSCODE_ERROR", "没有可处理任务类型 " + jobType + " 的 Provider"));
  }
}

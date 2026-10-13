package com.sts.asset.transcode;

import com.sts.asset.entity.AssetTranscodeJob;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
@ConditionalOnProperty(name = "sts.transcode.provider", havingValue = "stub", matchIfMissing = true)
public class StubTranscodeProvider implements TranscodeProvider {
  private static final Set<String> SUPPORTED = Set.of(
    "VIDEO_TRANSCODE", "AUDIO_TRANSCODE", "DOCUMENT_PREVIEW");

  @Override
  public boolean supports(String jobType) {
    return SUPPORTED.contains(jobType);
  }

  @Override
  public TranscodeOutput execute(AssetTranscodeJob job) {
    throw new TranscodeFailureException(
      "PROVIDER_DISABLED", "当前环境未启用真实转码服务，任务未执行");
  }
}

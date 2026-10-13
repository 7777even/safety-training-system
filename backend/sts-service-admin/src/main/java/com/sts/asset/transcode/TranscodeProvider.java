package com.sts.asset.transcode;

import com.sts.asset.entity.AssetTranscodeJob;

public interface TranscodeProvider {
  boolean supports(String jobType);

  TranscodeOutput execute(AssetTranscodeJob job);

  default void cleanup(TranscodeOutput output) {
  }
}

package com.sts.asset.transcode;

import java.util.List;

public record TranscodeOutput(
  Integer durationSeconds, Integer pageCount, List<TranscodeArtifact> artifacts,
  List<String> uploadedObjectNames) {
  public TranscodeOutput(Integer durationSeconds, List<TranscodeArtifact> artifacts,
                         List<String> uploadedObjectNames) {
    this(durationSeconds, null, artifacts, uploadedObjectNames);
  }

  public static TranscodeOutput empty() {
    return new TranscodeOutput(null, null, List.of(), List.of());
  }
}

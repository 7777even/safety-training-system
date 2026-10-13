package com.sts.asset.transcode;

public record TranscodeArtifact(
  String derivativeType, String objectName, String fileName, long fileSize, String fileSuffix) {
}

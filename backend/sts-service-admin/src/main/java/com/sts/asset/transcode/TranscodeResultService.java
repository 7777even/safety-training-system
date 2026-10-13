package com.sts.asset.transcode;

import com.sts.admin.entity.SysFileInfo;
import com.sts.admin.mapper.SysFileInfoMapper;
import com.sts.asset.entity.AssetDerivative;
import com.sts.asset.entity.AssetTranscodeJob;
import com.sts.asset.mapper.AssetDerivativeMapper;
import com.sts.asset.mapper.AssetMapper;
import com.sts.asset.mapper.AssetTranscodeJobMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class TranscodeResultService {
  private static final String VIDEO_TRANSCODE = "VIDEO_TRANSCODE";
  private static final String AUDIO_TRANSCODE = "AUDIO_TRANSCODE";
  private static final List<String> REQUIRED_VIDEO_ARTIFACTS = List.of("HLS", "THUMBNAIL");
  private static final List<String> REQUIRED_AUDIO_ARTIFACTS = List.of("HLS");

  private final AssetTranscodeJobMapper jobMapper;
  private final AssetMapper assetMapper;
  private final AssetDerivativeMapper derivativeMapper;
  private final SysFileInfoMapper fileInfoMapper;

  public void markProcessing(AssetTranscodeJob job) {
    assetMapper.updateProcessStatus(job.getAssetId(), job.getOrganizationId(), "PROCESSING");
  }

  @Transactional
  public void succeed(AssetTranscodeJob job, TranscodeOutput output) {
    validateOutput(job, output);
    for (TranscodeArtifact artifact : output.artifacts()) {
      SysFileInfo file = new SysFileInfo();
      file.setFileName(artifact.fileName());
      file.setBucketName("asset-derivative");
      file.setObjectName(artifact.objectName());
      file.setFileSize(artifact.fileSize());
      file.setFileSuffix(artifact.fileSuffix());
      file.setFileUrl("minio-private://" + artifact.objectName());
      fileInfoMapper.insert(file);

      AssetDerivative derivative = new AssetDerivative();
      derivative.setAssetId(job.getAssetId());
      derivative.setDerivativeType(artifact.derivativeType());
      derivative.setFileInfoId(file.getFileInfoId());
      derivative.setDurationSeconds(output.durationSeconds());
      derivative.setPageCount(output.pageCount());
      derivative.setStatus(1);
      derivativeMapper.insert(derivative);
    }
    if (jobMapper.finishSucceeded(job.getTranscodeJobId()) == 1) {
      assetMapper.finishTranscode(job.getAssetId(), job.getOrganizationId(),
                                  output.durationSeconds(), output.pageCount());
    } else {
      throw new IllegalStateException("转码任务终态已变化");
    }
  }

  private static void validateOutput(AssetTranscodeJob job, TranscodeOutput output) {
    String jobType = job.getJobType();
    if (VIDEO_TRANSCODE.equals(jobType)) {
      requireArtifacts(output, REQUIRED_VIDEO_ARTIFACTS, "视频转码缺少派生产物: ");
    } else if (AUDIO_TRANSCODE.equals(jobType)) {
      requireArtifacts(output, REQUIRED_AUDIO_ARTIFACTS, "音频转码缺少派生产物: ");
    }
  }

  private static void requireArtifacts(TranscodeOutput output, List<String> requiredTypes, String messagePrefix) {
    List<TranscodeArtifact> artifacts = output == null ? null : output.artifacts();
    List<String> missing = requiredTypes.stream()
      .filter(required -> artifacts == null || artifacts.stream()
        .filter(Objects::nonNull)
        .noneMatch(artifact -> required.equals(artifact.derivativeType())))
      .toList();
    if (!missing.isEmpty()) {
      throw new TranscodeFailureException("TRANSCODE_ERROR", messagePrefix + String.join(", ", missing));
    }
  }

  @Transactional
  public void fail(AssetTranscodeJob job, String failureCode, String failureReason) {
    if (jobMapper.finishFailed(job.getTranscodeJobId(), failureCode, failureReason) == 1) {
      assetMapper.updateProcessStatus(job.getAssetId(), job.getOrganizationId(), "FAILED");
    }
  }
}

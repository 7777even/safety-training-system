package com.sts.asset.transcode;

import com.sts.admin.entity.SysFileInfo;
import com.sts.admin.mapper.SysFileInfoMapper;
import com.sts.admin.storage.S3StorageGateway;
import com.sts.admin.storage.StorageObject;
import com.sts.admin.storage.TenantObjectKeyService;
import com.sts.asset.entity.Asset;
import com.sts.asset.entity.AssetTranscodeJob;
import com.sts.asset.mapper.AssetMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

@Component
@RequiredArgsConstructor
@ConditionalOnExpression("'${sts.transcode.provider:stub}' == 'ffmpeg'"
  + " && '${sts.transcode.document-provider:disabled}' == 'onlyoffice'")
public class DocPreviewProvider implements TranscodeProvider {
  private final AssetMapper assetMapper;
  private final SysFileInfoMapper fileInfoMapper;
  private final S3StorageGateway storage;
  private final OnlyOfficeClient client;
  private final OnlyOfficeProperties properties;
  private final OneTimeSourceTokenService tokens;
  private final PdfLinearizer linearizer;
  private final TenantObjectKeyService objectKeys;

  @Override
  public boolean supports(String jobType) {
    return "DOCUMENT_PREVIEW".equals(jobType);
  }

  @Override
  public TranscodeOutput execute(AssetTranscodeJob job) {
    Asset asset = assetMapper.selectById(job.getAssetId());
    SysFileInfo source = asset == null ? null : fileInfoMapper.selectById(asset.getFileInfoId());
    if (source == null || source.getFileSuffix() == null) {
      throw new TranscodeFailureException("SOURCE_INVALID", "文档源文件不存在");
    }
    String suffix = source.getFileSuffix().toLowerCase();
    if ("pdf".equals(suffix)) {
      if (source.getObjectName() == null || source.getObjectName().isBlank()) {
        throw new TranscodeFailureException("SOURCE_INVALID", "PDF 源文件不存在");
      }
      return linearizeStoredPdf(job, source.getObjectName());
    }
    String token = tokens.issue(job.getOrganizationId(), job.getTranscodeJobId(),
                                source.getFileInfoId(), Duration.ofMinutes(5));
    String sourceUrl = stripSlash(properties.getInternalSourceBaseUrl())
      + "/internal/asset/transcode-sources/" + token;
    byte[] pdf = client.convert(job.getTranscodeJobId(), suffix, sourceUrl);
    return linearizeAndUpload(job, new ByteArrayInputStream(pdf));
  }

  @Override
  public void cleanup(TranscodeOutput output) {
    if (output == null) return;
    output.uploadedObjectNames().forEach(key -> {
      try {
        storage.deleteObject(key);
      } catch (RuntimeException ignored) {
      }
    });
  }

  private static String stripSlash(String value) {
    return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
  }

  private TranscodeOutput linearizeAndUpload(AssetTranscodeJob job, InputStream input) {
    return linearizer.withLinearizedPdf(input, job.getTranscodeJobId(),
                                        (path, pages) -> upload(job, path, pages));
  }

  private TranscodeOutput linearizeStoredPdf(AssetTranscodeJob job, String objectName) {
    StorageObject object;
    try {
      object = storage.getObject(objectName);
    } catch (RuntimeException e) {
      throw new TranscodeFailureException("STORAGE_ERROR", "读取 PDF 源文件失败");
    }
    try (InputStream input = object.inputStream()) {
      return linearizeAndUpload(job, input);
    } catch (IOException e) {
      throw new TranscodeFailureException("STORAGE_ERROR", "读取 PDF 源文件失败");
    }
  }

  private TranscodeOutput upload(AssetTranscodeJob job, Path pdf, int pages) throws IOException {
    String key = objectKeys.forCurrentTenant(
      "derivative/" + job.getAssetId() + "/" + job.getTranscodeJobId() + "/preview/document.pdf");
    long size = Files.size(pdf);
    try (var input = Files.newInputStream(pdf)) {
      storage.putObject(key, input, size, "application/pdf");
    } catch (RuntimeException e) {
      try {
        storage.deleteObject(key);
      } catch (RuntimeException ignored) {
      }
      throw new TranscodeFailureException("STORAGE_ERROR", "上传文档预览失败");
    }
    TranscodeArtifact artifact = new TranscodeArtifact("PREVIEW", key, "document.pdf", size, "pdf");
    return new TranscodeOutput(null, pages, List.of(artifact), List.of(key));
  }
}

package com.sts.asset.transcode;

import com.sts.admin.entity.SysFileInfo;
import com.sts.admin.mapper.SysFileInfoMapper;
import com.sts.admin.storage.S3StorageGateway;
import com.sts.admin.storage.StorageObject;
import com.sts.admin.storage.TenantObjectKeyService;
import com.sts.asset.entity.Asset;
import com.sts.asset.entity.AssetTranscodeJob;
import com.sts.asset.mapper.AssetMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class DocPreviewProviderTest {
  @TempDir
  Path tempDir;

  private AssetMapper assetMapper;
  private SysFileInfoMapper fileInfoMapper;
  private S3StorageGateway storage;
  private OnlyOfficeClient client;
  private OnlyOfficeProperties properties;
  private OneTimeSourceTokenService tokens;
  private PdfPageTreeNormalizer normalizer;
  private DocPreviewProvider provider;
  private TenantObjectKeyService objectKeys;

  @BeforeEach
  void setUp() throws Exception {
    assetMapper = mock(AssetMapper.class);
    fileInfoMapper = mock(SysFileInfoMapper.class);
    storage = mock(S3StorageGateway.class);
    client = mock(OnlyOfficeClient.class);
    properties = new OnlyOfficeProperties();
    properties.setInternalSourceBaseUrl("http://sts/api");
    tokens = mock(OneTimeSourceTokenService.class);
    ExternalProcessRunner runner = mock(ExternalProcessRunner.class);
    when(runner.run(any(), eq(Duration.ofSeconds(30)))).thenAnswer(invocation -> {
      List<String> command = invocation.getArgument(0);
      if (command.contains("--linearize")) {
        Files.copy(Path.of(command.get(2)), Path.of(command.get(3)));
      }
      return new ProcessResult(0, "ok");
    });
    PdfPageCounter pageCounter = mock(PdfPageCounter.class);
    when(pageCounter.count(any(Path.class))).thenReturn(3);
    normalizer = mock(PdfPageTreeNormalizer.class);
    objectKeys = mock(TenantObjectKeyService.class);
    when(objectKeys.forCurrentTenant(any(String.class)))
      .thenAnswer(invocation -> "ACME/" + invocation.getArgument(0));
    doAnswer(invocation -> {
      Path source = invocation.getArgument(0);
      Path output = invocation.getArgument(1);
      Files.copy(source, output);
      return null;
    }).when(normalizer).normalize(any(Path.class), any(Path.class));
    when(normalizer.validate(any(Path.class), eq(3))).thenReturn(3);
    PdfLinearizer linearizer = new PdfLinearizer(
      new QpdfCommandFactory("/usr/bin/qpdf"), runner, pageCounter, normalizer,
      tempDir.toString(), 30);
    provider = new DocPreviewProvider(
      assetMapper, fileInfoMapper, storage, client, properties, tokens, linearizer, objectKeys);
  }

  @Test
  void nativePdfIsLinearizedWithoutCallingOnlyOffice() {
    givenSource("pdf", "source/manual.pdf");
    when(storage.getObject("source/manual.pdf")).thenReturn(
      new StorageObject(new ByteArrayInputStream("native-pdf".getBytes()), 10, "application/pdf"));

    TranscodeOutput output = provider.execute(job());

    assertThat(output.pageCount()).isEqualTo(3);
    assertThat(output.artifacts()).singleElement().satisfies(artifact -> {
      assertThat(artifact.derivativeType()).isEqualTo("PREVIEW");
      assertThat(artifact.objectName()).isEqualTo("ACME/derivative/20/9/preview/document.pdf");
    });
    verifyNoInteractions(client, tokens);
    verifyUploadedBytes("native-pdf");
  }

  @Test
  void officePdfIsLinearizedBeforeUpload() {
    givenSource("docx", "source/manual.docx");
    when(tokens.issue(1L, 9L, 30L, Duration.ofMinutes(5))).thenReturn("token");
    when(client.convert(9L, "docx", "http://sts/api/internal/asset/transcode-sources/token"))
      .thenReturn("office-pdf".getBytes());

    TranscodeOutput output = provider.execute(job());

    assertThat(output.pageCount()).isEqualTo(3);
    verifyUploadedBytes("office-pdf");
  }

  @Test
  void deletesPossiblyWrittenObjectWhenUploadFails() {
    givenSource("pdf", "source/manual.pdf");
    when(storage.getObject("source/manual.pdf")).thenReturn(
      new StorageObject(new ByteArrayInputStream("native-pdf".getBytes()), 10, "application/pdf"));
    doThrow(new IllegalStateException("partial upload"))
      .when(storage).putObject(eq("ACME/derivative/20/9/preview/document.pdf"), any(), anyLong(), any());

    assertThatThrownBy(() -> provider.execute(job()))
      .isInstanceOf(TranscodeFailureException.class)
      .hasMessage("上传文档预览失败");
    verify(storage).deleteObject("ACME/derivative/20/9/preview/document.pdf");
  }

  @Test
  void doesNotUploadPreviewWhenFinalPageTreeValidationFails() throws Exception {
    givenSource("pdf", "source/manual.pdf");
    when(storage.getObject("source/manual.pdf")).thenReturn(
      new StorageObject(new ByteArrayInputStream("native-pdf".getBytes()), 10, "application/pdf"));
    doThrow(new TranscodeFailureException("TRANSCODE_ERROR", "PDF 页面树结构校验失败"))
      .when(normalizer).validate(any(Path.class), eq(3));

    assertThatThrownBy(() -> provider.execute(job()))
      .isInstanceOf(TranscodeFailureException.class)
      .hasMessage("PDF 页面树结构校验失败");
    verify(storage, never()).putObject(any(), any(), anyLong(), any());
    try (var children = Files.list(tempDir)) {
      assertThat(children).isEmpty();
    }
  }

  private void givenSource(String suffix, String objectName) {
    Asset asset = new Asset();
    asset.setAssetId(20L);
    asset.setFileInfoId(30L);
    SysFileInfo file = new SysFileInfo();
    file.setFileInfoId(30L);
    file.setFileSuffix(suffix);
    file.setObjectName(objectName);
    when(assetMapper.selectById(20L)).thenReturn(asset);
    when(fileInfoMapper.selectById(30L)).thenReturn(file);
  }

  private void verifyUploadedBytes(String expected) {
    verify(storage).putObject(eq("ACME/derivative/20/9/preview/document.pdf"), any(),
                              eq((long) expected.length()), eq("application/pdf"));
  }

  private static AssetTranscodeJob job() {
    AssetTranscodeJob job = new AssetTranscodeJob();
    job.setTranscodeJobId(9L);
    job.setAssetId(20L);
    job.setOrganizationId(1L);
    job.setJobType("DOCUMENT_PREVIEW");
    return job;
  }
}

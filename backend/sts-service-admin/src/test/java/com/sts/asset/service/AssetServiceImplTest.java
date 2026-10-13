package com.sts.asset.service;

import com.sts.admin.entity.SysFileInfo;
import com.sts.admin.mapper.SysFileInfoMapper;
import com.sts.admin.tenant.TenantContext;
import com.sts.asset.converter.AssetConverter;
import com.sts.asset.dto.AssetCreateRequest;
import com.sts.asset.entity.Asset;
import com.sts.asset.entity.AssetTranscodeJob;
import com.sts.asset.mapper.AssetMapper;
import com.sts.asset.mapper.AssetTranscodeJobMapper;
import com.sts.common.exception.BusinessException;
import com.sts.course.service.CourseAssetReferenceService;
import com.sts.course.vo.CourseAssetReferenceVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AssetServiceImplTest {

  private static final Long ORG_ID = 1L;
  private static final Long ASSET_ID = 100L;
  private static final Long FILE_INFO_ID = 200L;

  private AssetMapper assetMapper;
  private AssetTranscodeJobMapper transcodeJobMapper;
  private AssetConverter assetConverter;
  private SysFileInfoMapper sysFileInfoMapper;
  private CourseAssetReferenceService courseAssetReferenceService;
  private AssetServiceImpl service;

  @BeforeEach
  void setUp() {
    TenantContext.setTenantId(ORG_ID_FOR_TENANT);
    assetMapper = mock(AssetMapper.class);
    transcodeJobMapper = mock(AssetTranscodeJobMapper.class);
    assetConverter = Mappers.getMapper(AssetConverter.class);
    sysFileInfoMapper = mock(SysFileInfoMapper.class);
    courseAssetReferenceService = mock(CourseAssetReferenceService.class);
    service = new AssetServiceImpl(
      assetMapper, transcodeJobMapper, assetConverter,
      sysFileInfoMapper, courseAssetReferenceService, new AssetOriginalFileSizeValidator());
  }

  @AfterEach
  void clearTenantContext() {
    TenantContext.clear();
  }

  private static final Long ORG_ID_FOR_TENANT = 1L;

  @Test
  @DisplayName("createAsset：PDF DOCUMENT UPLOAD → DOCUMENT_PREVIEW PENDING")
  void createAsset_pdfDocumentUpload_createsPendingPreviewJob() {
    SysFileInfo fileInfo = new SysFileInfo();
    fileInfo.setFileInfoId(FILE_INFO_ID);
    fileInfo.setFileSuffix("pdf");
    when(sysFileInfoMapper.selectById(FILE_INFO_ID)).thenReturn(fileInfo);
    doAnswer(invocation -> {
      Asset entity = invocation.getArgument(0);
      entity.setAssetId(ASSET_ID);
      return 1;
    }).when(assetMapper).insert(any(Asset.class));

    AssetCreateRequest request = uploadRequest("DOC-001", "安全手册", "DOCUMENT");

    Long assetId = service.createAsset(request);

    assertThat(assetId).isEqualTo(ASSET_ID);
    verify(assetMapper).insert(any(Asset.class));
    verify(transcodeJobMapper).insert(argThat((AssetTranscodeJob job) ->
                                                "DOCUMENT_PREVIEW".equals(job.getJobType()) && "PENDING".equals(job.getJobStatus())));
    verify(assetMapper, never()).updateById(any(Asset.class));
  }

  @Test
  @DisplayName("createAsset：VIDEO UPLOAD → 只创建 PENDING 任务并保持 UPLOADED")
  void createAsset_videoUpload_createsPendingJobAsynchronously() {
    SysFileInfo fileInfo = new SysFileInfo();
    fileInfo.setFileInfoId(FILE_INFO_ID);
    when(sysFileInfoMapper.selectById(FILE_INFO_ID)).thenReturn(fileInfo);
    doAnswer(invocation -> {
      Asset entity = invocation.getArgument(0);
      entity.setAssetId(ASSET_ID);
      entity.setOrganizationId(ORG_ID);
      return 1;
    }).when(assetMapper).insert(any(Asset.class));

    AssetCreateRequest request = uploadRequest("VID-001", "培训视频", "VIDEO");

    Long assetId = service.createAsset(request);

    assertThat(assetId).isEqualTo(ASSET_ID);
    ArgumentCaptor<AssetTranscodeJob> jobCaptor = ArgumentCaptor.forClass(AssetTranscodeJob.class);
    verify(transcodeJobMapper).insert(jobCaptor.capture());
    AssetTranscodeJob job = jobCaptor.getValue();
    assertThat(job.getJobType()).isEqualTo("VIDEO_TRANSCODE");
    assertThat(job.getAssetId()).isEqualTo(ASSET_ID);
    assertThat(job.getJobStatus()).isEqualTo("PENDING");
    verify(transcodeJobMapper, never()).updateById(any(AssetTranscodeJob.class));
    verify(assetMapper, never()).updateById(any(Asset.class));
  }

  @Test
  @DisplayName("createAsset：DOCX DOCUMENT UPLOAD → DOCUMENT_PREVIEW PENDING")
  void createAsset_docxCreatesDocumentPreviewJob() {
    SysFileInfo fileInfo = new SysFileInfo();
    fileInfo.setFileInfoId(FILE_INFO_ID);
    fileInfo.setFileSuffix("docx");
    when(sysFileInfoMapper.selectById(FILE_INFO_ID)).thenReturn(fileInfo);
    doAnswer(invocation -> {
      Asset entity = invocation.getArgument(0);
      entity.setAssetId(ASSET_ID);
      return 1;
    }).when(assetMapper).insert(any(Asset.class));

    service.createAsset(uploadRequest("DOCX-001", "安全手册", "DOCUMENT"));

    verify(transcodeJobMapper).insert(argThat((AssetTranscodeJob job) ->
                                                "DOCUMENT_PREVIEW".equals(job.getJobType()) && "PENDING".equals(job.getJobStatus())));
    verify(assetMapper, never()).updateById(any(Asset.class));
  }

  @Test
  @DisplayName("createAsset：EXTERNAL → 直接 SUCCEEDED")
  void createAsset_external_succeeded() {
    doAnswer(invocation -> {
      Asset entity = invocation.getArgument(0);
      entity.setAssetId(ASSET_ID);
      return 1;
    }).when(assetMapper).insert(any(Asset.class));

    AssetCreateRequest request = new AssetCreateRequest();
    request.setAssetCode("LINK-001");
    request.setAssetName("外部链接");
    request.setAssetType("LINK");
    request.setSourceType("EXTERNAL");
    request.setExternalUrl("https://example.com/doc");

    Long assetId = service.createAsset(request);

    assertThat(assetId).isEqualTo(ASSET_ID);
    ArgumentCaptor<Asset> insertCaptor = ArgumentCaptor.forClass(Asset.class);
    verify(assetMapper).insert(insertCaptor.capture());
    Asset inserted = insertCaptor.getValue();
    assertThat(inserted.getProcessStatus()).isEqualTo("SUCCEEDED");
    assertThat(inserted.getFileInfoId()).isNull();
    verify(assetMapper, never()).updateById(any(Asset.class));
    verify(transcodeJobMapper, never()).insert(any(AssetTranscodeJob.class));
  }

  @Test
  @DisplayName("retryTranscode：FAILED 文档任务只重置为 PENDING，不同步执行 Provider")
  void retryTranscode_documentResetsPendingOnly() {
    Asset asset = asset(ASSET_ID, "FAILED");
    asset.setAssetType("DOCUMENT");
    when(assetMapper.selectOne(any())).thenReturn(asset);
    AssetTranscodeJob job = new AssetTranscodeJob();
    job.setTranscodeJobId(9L);
    job.setAssetId(ASSET_ID);
    job.setJobType("DOCUMENT_PREVIEW");
    job.setJobStatus("FAILED");
    job.setRetryCount(0);
    when(transcodeJobMapper.selectOne(any())).thenReturn(job);

    service.retryTranscode(ASSET_ID);

    verify(transcodeJobMapper).updateById(argThat((AssetTranscodeJob updated) ->
                                                    "PENDING".equals(updated.getJobStatus()) && updated.getRetryCount() == 1));
    verify(assetMapper).updateById(argThat((Asset updated) ->
                                             "UPLOADED".equals(updated.getProcessStatus())));
  }

  @Test
  @DisplayName("deleteAsset：被课程引用 → 409 / 6023")
  void deleteAsset_referenced_throws6023() {
    Asset asset = asset(ASSET_ID, "SUCCEEDED");
    when(assetMapper.selectOne(any())).thenReturn(asset);

    CourseAssetReferenceVO refs = new CourseAssetReferenceVO();
    refs.setReferenceCount(2L);
    when(courseAssetReferenceService.getReferences(ASSET_ID)).thenReturn(refs);

    assertThatThrownBy(() -> service.deleteAsset(ASSET_ID))
      .isInstanceOf(BusinessException.class)
      .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(6023));
    verify(assetMapper, never()).update(any(), any());
  }

  @Test
  @DisplayName("createAsset：图片超过 16MB → 400 / 6025 且不落库")
  void createAsset_imageOver16Mb_throws6025() {
    SysFileInfo fileInfo = new SysFileInfo();
    fileInfo.setFileInfoId(FILE_INFO_ID);
    fileInfo.setFileSize(16L * 1024 * 1024 + 1);
    when(sysFileInfoMapper.selectById(FILE_INFO_ID)).thenReturn(fileInfo);

    assertThatThrownBy(() -> service.createAsset(uploadRequest("IMG-001", "过大图片", "IMAGE")))
      .isInstanceOfSatisfying(BusinessException.class, error -> {
        assertThat(error.getCode()).isEqualTo(6025);
        assertThat(error.getMessage()).isEqualTo("图片原始文件不能超过 16MB");
      });
    verify(assetMapper, never()).insert(any(Asset.class));
  }

  private static AssetCreateRequest uploadRequest(String code, String name, String assetType) {
    AssetCreateRequest request = new AssetCreateRequest();
    request.setAssetCode(code);
    request.setAssetName(name);
    request.setAssetType(assetType);
    request.setSourceType("UPLOAD");
    request.setFileInfoId(FILE_INFO_ID);
    return request;
  }

  private static Asset asset(Long assetId, String processStatus) {
    Asset asset = new Asset();
    asset.setAssetId(assetId);
    asset.setOrganizationId(ORG_ID);
    asset.setProcessStatus(processStatus);
    asset.setStatus(1);
    return asset;
  }
}

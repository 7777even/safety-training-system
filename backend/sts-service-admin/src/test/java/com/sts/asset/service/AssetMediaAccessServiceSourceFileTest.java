package com.sts.asset.service;

import com.sts.admin.entity.SysFileInfo;
import com.sts.admin.mapper.SysFileInfoMapper;
import com.sts.admin.storage.S3StorageGateway;
import com.sts.admin.storage.StorageObject;
import com.sts.admin.storage.TenantObjectKeyService;
import com.sts.asset.entity.Asset;
import com.sts.asset.mapper.AssetDerivativeMapper;
import com.sts.asset.mapper.AssetMapper;
import com.sts.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.ByteArrayInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class AssetMediaAccessServiceSourceFileTest {

  private AssetMapper assetMapper;
  private SysFileInfoMapper fileInfoMapper;
  private S3StorageGateway storage;
  private AssetDerivativeMapper derivativeMapper;
  private TenantObjectKeyService objectKeys;
  private AssetMediaAccessService service;

  @BeforeEach
  void setUp() {
    assetMapper = mock(AssetMapper.class);
    fileInfoMapper = mock(SysFileInfoMapper.class);
    storage = mock(S3StorageGateway.class);
    derivativeMapper = mock(AssetDerivativeMapper.class);
    objectKeys = mock(TenantObjectKeyService.class);
    when(objectKeys.forCurrentTenant(anyString())).thenAnswer(invocation -> "ACME/" + invocation.getArgument(0));
    service = new AssetMediaAccessService(
      assetMapper, derivativeMapper, fileInfoMapper, storage, objectKeys);
  }

  @Test
  @DisplayName("sourceFile 仅输出启用且处理成功的 UPLOAD 原始文件")
  void sourceFileReadsOnlyValidOriginalFile() {
    when(assetMapper.selectById(30L)).thenReturn(asset(1, "SUCCEEDED", "UPLOAD", 301L));
    when(fileInfoMapper.selectById(301L)).thenReturn(file("course/manual.pdf"));
    when(storage.objectSize("course/manual.pdf")).thenReturn(10L);
    when(storage.getObject("course/manual.pdf")).thenReturn(
      new StorageObject(new ByteArrayInputStream(new byte[10]), 10, "application/pdf"));

    var result = service.sourceFile(30L, 301L, null);

    assertThat(result.totalSize()).isEqualTo(10);
    verify(storage).getObject("course/manual.pdf");
  }

  @Test
  @DisplayName("sourceFile 拒绝停用或非 UPLOAD 素材且不访问存储")
  void sourceFileRejectsDisabledOrNonUploadAsset() {
    for (Asset asset : new Asset[]{
      asset(0, "SUCCEEDED", "UPLOAD", 301L),
      asset(1, "SUCCEEDED", "LINK", 301L),
      asset(1, "PROCESSING", "UPLOAD", 301L)
    }) {
      reset(assetMapper, fileInfoMapper, storage);
      when(assetMapper.selectById(30L)).thenReturn(asset);
      assertThatThrownBy(() -> service.sourceFile(30L, 301L, null))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("code", 6011);
      verifyNoInteractions(fileInfoMapper, storage);
    }
  }

  @Test
  @DisplayName("sourceFile 将对象存储 404 归一为素材文件不存在")
  void sourceFileMapsStorage404ToNotFound() {
    when(assetMapper.selectById(30L)).thenReturn(asset(1, "SUCCEEDED", "UPLOAD", 301L));
    when(fileInfoMapper.selectById(301L)).thenReturn(file("course/missing.pdf"));
    when(storage.objectSize("course/missing.pdf"))
      .thenThrow(S3Exception.builder().statusCode(404).message("missing").build());

    assertThatThrownBy(() -> service.sourceFile(30L, 301L, null))
      .isInstanceOf(BusinessException.class)
      .hasFieldOrPropertyWithValue("code", 6011);
  }

  @Test
  @DisplayName("HLS 以已登记 manifest 目录读取新旧 segment")
  void hlsReadsSegmentFromRegisteredTenantOrLegacyManifestDirectory() {
    when(assetMapper.selectById(30L)).thenReturn(asset(1, "SUCCEEDED", "UPLOAD", 301L));
    when(derivativeMapper.selectCount(any())).thenReturn(1L);
    SysFileInfo tenantManifest = file("ACME/derivative/30/9/hls/index.m3u8");
    when(fileInfoMapper.selectOne(any())).thenReturn(tenantManifest);
    when(storage.objectSize("ACME/derivative/30/9/hls/segment-00001.ts")).thenReturn(3L);
    when(storage.getObject("ACME/derivative/30/9/hls/segment-00001.ts")).thenReturn(
      new StorageObject(new ByteArrayInputStream(new byte[3]), 3, "video/mp2t"));

    assertThat(service.hls(30L, 9L, "segment-00001.ts", null).totalSize()).isEqualTo(3L);
    verify(storage).getObject("ACME/derivative/30/9/hls/segment-00001.ts");

    reset(fileInfoMapper, storage);
    SysFileInfo legacyManifest = file("derivative/30/9/hls/index.m3u8");
    when(fileInfoMapper.selectOne(any())).thenReturn(null, legacyManifest);
    when(storage.objectSize("derivative/30/9/hls/index.m3u8")).thenReturn(4L);
    when(storage.getObject("derivative/30/9/hls/index.m3u8")).thenReturn(
      new StorageObject(new ByteArrayInputStream(new byte[4]), 4, "application/vnd.apple.mpegurl"));

    assertThat(service.hls(30L, 9L, "index.m3u8", null).totalSize()).isEqualTo(4L);
    verify(storage).getObject("derivative/30/9/hls/index.m3u8");
  }

  private static Asset asset(int status, String processStatus, String sourceType, Long fileInfoId) {
    Asset asset = new Asset();
    asset.setAssetId(30L);
    asset.setStatus(status);
    asset.setProcessStatus(processStatus);
    asset.setSourceType(sourceType);
    asset.setFileInfoId(fileInfoId);
    return asset;
  }

  private static SysFileInfo file(String objectName) {
    SysFileInfo file = new SysFileInfo();
    file.setFileInfoId(301L);
    file.setObjectName(objectName);
    return file;
  }
}

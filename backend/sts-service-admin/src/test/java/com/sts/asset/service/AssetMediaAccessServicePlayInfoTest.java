package com.sts.asset.service;

import com.sts.admin.entity.SysFileInfo;
import com.sts.admin.mapper.SysFileInfoMapper;
import com.sts.admin.storage.S3StorageGateway;
import com.sts.admin.storage.TenantObjectKeyService;
import com.sts.asset.entity.Asset;
import com.sts.asset.entity.AssetDerivative;
import com.sts.asset.mapper.AssetDerivativeMapper;
import com.sts.asset.mapper.AssetMapper;
import com.sts.asset.vo.AssetPlayInfoVO;
import com.sts.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@code play-info} 的 format 取值口径。
 * <p>
 * format 表达的是「返回的这个 url 该用什么组件渲染」，取值一一对应
 * {@code docs/architecture/asset-transcode-design.md} §6.3 的预览组件表，
 * 且 MUST 落在契约枚举 {@code hls/pdf/excel/image/original} 之内。
 * <p>
 * 此前无派生产物时直接返回源文件后缀（开放集合），既超出枚举，
 * 又把「转码失败的视频」伪装成「可直接播放的 mp4」，掩盖故障。
 */
class AssetMediaAccessServicePlayInfoTest {

  private AssetMapper assetMapper;
  private AssetDerivativeMapper derivativeMapper;
  private SysFileInfoMapper fileInfoMapper;
  private AssetMediaAccessService service;

  @BeforeEach
  void setUp() {
    assetMapper = mock(AssetMapper.class);
    derivativeMapper = mock(AssetDerivativeMapper.class);
    fileInfoMapper = mock(SysFileInfoMapper.class);
    service = new AssetMediaAccessService(assetMapper, derivativeMapper, fileInfoMapper,
                                          mock(S3StorageGateway.class), mock(TenantObjectKeyService.class));
  }

  private void givenAsset(Asset asset, AssetDerivative... derivatives) {
    asset.setAssetId(20L);
    asset.setProcessStatus("SUCCEEDED");
    when(assetMapper.selectById(20L)).thenReturn(asset);
    when(derivativeMapper.selectList(any())).thenReturn(List.of(derivatives));
  }

  private static Asset asset(String assetType, Long fileInfoId) {
    Asset asset = new Asset();
    asset.setAssetType(assetType);
    asset.setFileInfoId(fileInfoId);
    return asset;
  }

  private static AssetDerivative derivative(String type, Long fileInfoId) {
    AssetDerivative derivative = new AssetDerivative();
    derivative.setDerivativeType(type);
    derivative.setFileInfoId(fileInfoId);
    return derivative;
  }

  private void givenSourceSuffix(Long fileInfoId, String suffix) {
    SysFileInfo file = new SysFileInfo();
    file.setFileInfoId(fileInfoId);
    file.setFileSuffix(suffix);
    when(fileInfoMapper.selectById(fileInfoId)).thenReturn(file);
  }

  @Test
  @DisplayName("视频有 HLS 派生：format=hls，url 指向 m3u8")
  void video_withHlsDerivative_returnsHls() {
    SysFileInfo manifest = new SysFileInfo();
    manifest.setObjectName("derivative/20/9/hls/index.m3u8");
    when(fileInfoMapper.selectById(88L)).thenReturn(manifest);
    givenAsset(asset("VIDEO", 5L), derivative("HLS", 88L));

    AssetPlayInfoVO vo = service.playInfo(20L);

    assertThat(vo.format()).isEqualTo("hls");
    assertThat(vo.url()).isEqualTo("/api/asset/files/20/hls/9/index.m3u8");
  }

  @Test
  @DisplayName("带租户根目录的 HLS 仍从对象键解析正确任务 ID")
  void video_withTenantPrefixedHlsDerivative_returnsCorrectJobId() {
    SysFileInfo manifest = new SysFileInfo();
    manifest.setObjectName("ACME/derivative/20/9/hls/index.m3u8");
    when(fileInfoMapper.selectById(88L)).thenReturn(manifest);
    givenAsset(asset("VIDEO", 5L), derivative("HLS", 88L));

    assertThat(service.playInfo(20L).url())
      .isEqualTo("/api/asset/files/20/hls/9/index.m3u8");
  }

  @Test
  @DisplayName("音频有 HLS 派生：format=hls，url 指向 m3u8，不要求封面")
  void audio_withHlsDerivative_returnsHls() {
    SysFileInfo manifest = new SysFileInfo();
    manifest.setObjectName("derivative/20/9/hls/index.m3u8");
    when(fileInfoMapper.selectById(88L)).thenReturn(manifest);
    givenAsset(asset("AUDIO", 5L), derivative("HLS", 88L));

    AssetPlayInfoVO vo = service.playInfo(20L);

    assertThat(vo.format()).isEqualTo("hls");
    assertThat(vo.url()).isEqualTo("/api/asset/files/20/hls/9/index.m3u8");
    assertThat(vo.coverUrl()).isNull();
  }

  @Test
  @DisplayName("带租户根目录的音频 HLS 仍从对象键解析正确任务 ID")
  void audio_withTenantPrefixedHlsDerivative_returnsCorrectJobId() {
    SysFileInfo manifest = new SysFileInfo();
    manifest.setObjectName("ACME/derivative/20/9/hls/index.m3u8");
    when(fileInfoMapper.selectById(88L)).thenReturn(manifest);
    givenAsset(asset("AUDIO", 5L), derivative("HLS", 88L));

    assertThat(service.playInfo(20L).url())
      .isEqualTo("/api/asset/files/20/hls/9/index.m3u8");
  }

  @Test
  @DisplayName("音频同时有 HLS 与遗留 TRANSCODED：只用 HLS")
  void audio_withHlsAndTranscoded_usesHls() {
    SysFileInfo manifest = new SysFileInfo();
    manifest.setObjectName("derivative/20/9/hls/index.m3u8");
    when(fileInfoMapper.selectById(88L)).thenReturn(manifest);
    givenAsset(asset("AUDIO", 5L), derivative("TRANSCODED", 77L), derivative("HLS", 88L));

    AssetPlayInfoVO vo = service.playInfo(20L);

    assertThat(vo.format()).isEqualTo("hls");
    assertThat(vo.url()).isEqualTo("/api/asset/files/20/hls/9/index.m3u8");
  }

  @Test
  @DisplayName("音频仅有遗留 TRANSCODED、无 HLS：format=original，不返回 mp3")
  void audio_withOnlyTranscodedDerivative_returnsOriginal() {
    givenAsset(asset("AUDIO", 5L), derivative("TRANSCODED", 88L));

    AssetPlayInfoVO vo = service.playInfo(20L);

    assertThat(vo.format()).isEqualTo("original");
    assertThat(vo.url()).isEqualTo("/api/asset/files/20/5");
  }

  @Test
  @DisplayName("音频缺少 HLS 与 TRANSCODED：format=original")
  void audio_withoutHlsOrTranscoded_returnsOriginal() {
    givenAsset(asset("AUDIO", 5L));
    givenSourceSuffix(5L, "wav");

    assertThat(service.playInfo(20L).format())
      .describedAs("MUST NOT 把未转码音频伪装成可播放的 wav/mp3").isEqualTo("original");
  }

  @Test
  @DisplayName("文档有 PREVIEW 派生：format=pdf")
  void document_withPreviewDerivative_returnsPdf() {
    givenAsset(asset("DOCUMENT", 5L), derivative("PREVIEW", 88L));

    assertThat(service.playInfo(20L).format()).isEqualTo("pdf");
  }

  @Test
  @DisplayName("pdf 原生文档无派生：format=pdf，原文件即预览载体")
  void document_nativePdfWithoutDerivative_returnsPdf() {
    givenAsset(asset("DOCUMENT", 5L));
    givenSourceSuffix(5L, "pdf");

    AssetPlayInfoVO vo = service.playInfo(20L);

    assertThat(vo.format()).isEqualTo("pdf");
    assertThat(vo.url()).isEqualTo("/api/asset/files/20/5");
  }

  @Test
  @DisplayName("Excel 无派生：format=excel，前端表格组件直显")
  void document_excel_returnsExcel() {
    for (String suffix : new String[]{"xls", "xlsx", "XLSX"}) {
      setUp();
      givenAsset(asset("DOCUMENT", 5L));
      givenSourceSuffix(5L, suffix);

      assertThat(service.playInfo(20L).format())
        .describedAs("后缀 %s 应归一为 excel", suffix).isEqualTo("excel");
    }
  }

  @Test
  @DisplayName("图片：format=image，不与 original 混用")
  void image_returnsImage() {
    givenAsset(asset("IMAGE", 5L));

    assertThat(service.playInfo(20L).format()).isEqualTo("image");
  }

  @Test
  @DisplayName("不支持预览的文档后缀：format=original")
  void document_unsupportedSuffix_returnsOriginal() {
    givenAsset(asset("DOCUMENT", 5L));
    givenSourceSuffix(5L, "txt");

    assertThat(service.playInfo(20L).format()).isEqualTo("original");
  }

  /**
   * 这是本次改动最重要的一条：转码失败或派生丢失的视频，
   * 此前返回 {@code mp4} 看起来像"可直接播放"，掩盖了故障；
   * 现在返回 {@code original}，而"视频拿到 original"本身就是可观测的异常信号。
   */
  @Test
  @DisplayName("视频缺少 HLS 派生：format=original，MUST NOT 返回 mp4")
  void video_withoutHlsDerivative_returnsOriginalNotSourceSuffix() {
    givenAsset(asset("VIDEO", 5L));
    givenSourceSuffix(5L, "mp4");

    assertThat(service.playInfo(20L).format())
      .describedAs("MUST NOT 把转码失败伪装成可播放的 mp4").isEqualTo("original");
  }

  @Test
  @DisplayName("外链与富文本没有可播文件：返回 404 而不是带 null 的坏地址")
  void linkAndRichText_withoutSourceFile_areRejected() {
    for (String assetType : new String[]{"LINK", "RICH_TEXT"}) {
      setUp();
      givenAsset(asset(assetType, null));

      assertThatThrownBy(() -> service.playInfo(20L))
        .describedAs("%s 无源文件，此前会拼出 /api/asset/files/20/null", assetType)
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("code", 6011);
    }
  }
}

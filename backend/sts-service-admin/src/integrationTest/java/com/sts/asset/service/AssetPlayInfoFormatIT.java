package com.sts.asset.service;

import com.sts.admin.mapper.SysFileInfoMapper;
import com.sts.admin.storage.S3StorageGateway;
import com.sts.admin.storage.TenantObjectKeyService;
import com.sts.admin.support.AbstractMysqlIntegrationTest;
import com.sts.asset.mapper.AssetDerivativeMapper;
import com.sts.asset.mapper.AssetMapper;
import com.sts.asset.support.AssetMapperItConfig;
import com.sts.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * {@code play-info} 的 format 在真实数据形态下的取值（Testcontainers 真 MySQL）。
 * <p>
 * 零 DB 用例用 mock 拼装素材与派生文件，证明分支逻辑；本用例证明在真库里
 * 「有派生文件」与「无派生文件」这两种形态确实按预期落到对应取值——
 * 尤其是 Excel 归一为 {@code excel}、以及视频缺派生时不再回显 {@code mp4}。
 */
@SpringBootTest(classes = AssetMapperItConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class AssetPlayInfoFormatIT extends AbstractMysqlIntegrationTest {

  @Autowired
  private AssetMapper assetMapper;
  @Autowired
  private AssetDerivativeMapper derivativeMapper;
  @Autowired
  private SysFileInfoMapper fileInfoMapper;
  @Autowired
  private JdbcTemplate jdbc;

  private AssetMediaAccessService service;

  @BeforeEach
  void setUp() {
    service = new AssetMediaAccessService(assetMapper, derivativeMapper, fileInfoMapper,
                                          mock(S3StorageGateway.class), mock(TenantObjectKeyService.class));
  }

  private Long insertFile(String objectName, String suffix) {
    jdbc.update("""
                  INSERT INTO us_sys_file_info
                    (organization_id, file_name, bucket_name, object_name, file_size, file_suffix, file_url)
                  VALUES (?, ?, 'sts-private', ?, 1024, ?, ?)
                  """, demoTenantId(), "f-" + objectName, objectName, suffix,
                "minio-private://" + objectName);
    return jdbc.queryForObject(
      "SELECT file_info_id FROM us_sys_file_info WHERE object_name = ?", Long.class, objectName);
  }

  private Long insertAsset(String code, String assetType, Long fileInfoId) {
    jdbc.update("""
                  INSERT INTO sts_asset_asset
                    (organization_id, asset_code, asset_name, asset_type, source_type,
                     file_info_id, process_status, status)
                  VALUES (?, ?, ?, ?, ?, ?, 'SUCCEEDED', 1)
                  """, demoTenantId(), code, "素材-" + code, assetType,
                fileInfoId == null ? "EXTERNAL" : "UPLOAD", fileInfoId);
    return jdbc.queryForObject(
      "SELECT asset_id FROM sts_asset_asset WHERE asset_code = ?", Long.class, code);
  }

  private void insertDerivative(Long assetId, String derivativeType, Long fileInfoId) {
    jdbc.update("""
                  INSERT INTO sts_asset_derivative
                    (organization_id, asset_id, derivative_type, file_info_id, status)
                  VALUES (?, ?, ?, ?, 1)
                  """, demoTenantId(), assetId, derivativeType, fileInfoId);
  }

  @Test
  @DisplayName("视频有 HLS 派生 → hls，url 指向 m3u8")
  void video_withHls_returnsHls() {
    Long assetId = insertAsset("PF-VIDEO", "VIDEO", insertFile("source/pf-video.mp4", "mp4"));
    insertDerivative(assetId, "HLS", insertFile("derivative/" + assetId + "/9/hls/index.m3u8", "m3u8"));

    var vo = service.playInfo(assetId);

    assertThat(vo.format()).isEqualTo("hls");
    assertThat(vo.url()).isEqualTo("/api/asset/files/" + assetId + "/hls/9/index.m3u8");
  }

  @Test
  @DisplayName("视频缺 HLS 派生 → original，MUST NOT 回显 mp4")
  void video_withoutHls_returnsOriginal() {
    Long assetId = insertAsset("PF-VIDEO-BROKEN", "VIDEO", insertFile("source/pf-broken.mp4", "mp4"));

    assertThat(service.playInfo(assetId).format())
      .describedAs("这正是转码失败时的形态，必须暴露为异常信号而不是伪装成可播 mp4")
      .isEqualTo("original");
  }

  @Test
  @DisplayName("xls 与 xlsx 都归一为 excel")
  void excel_bothSuffixes_returnExcel() {
    Long xls = insertAsset("PF-XLS", "DOCUMENT", insertFile("source/pf.xls", "xls"));
    Long xlsx = insertAsset("PF-XLSX", "DOCUMENT", insertFile("source/pf.xlsx", "XLSX"));

    assertThat(service.playInfo(xls).format()).isEqualTo("excel");
    assertThat(service.playInfo(xlsx).format())
      .describedAs("大写后缀同样归一").isEqualTo("excel");
  }

  @Test
  @DisplayName("pdf 原生文档无派生 → pdf；不支持预览的后缀 → original")
  void document_pdfNativeAndUnsupportedSuffix() {
    Long pdf = insertAsset("PF-PDF", "DOCUMENT", insertFile("source/pf.pdf", "pdf"));
    Long txt = insertAsset("PF-TXT", "DOCUMENT", insertFile("source/pf.txt", "txt"));

    assertThat(service.playInfo(pdf).format()).isEqualTo("pdf");
    assertThat(service.playInfo(txt).format()).isEqualTo("original");
  }

  @Test
  @DisplayName("图片 → image，与 original 区分开")
  void image_returnsImage() {
    Long assetId = insertAsset("PF-IMG", "IMAGE", insertFile("source/pf.png", "png"));

    assertThat(service.playInfo(assetId).format()).isEqualTo("image");
  }

  @Test
  @DisplayName("外链无源文件 → 404，不再返回带 null 的坏地址")
  void externalLink_withoutSourceFile_returns404() {
    Long assetId = insertAsset("PF-LINK", "LINK", null);

    assertThatThrownBy(() -> service.playInfo(assetId))
      .isInstanceOf(BusinessException.class)
      .hasFieldOrPropertyWithValue("code", 6011);
  }
}

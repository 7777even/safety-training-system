package com.sts.course.service;

import com.sts.admin.entity.SysFileInfo;
import com.sts.admin.mapper.SysFileInfoMapper;
import com.sts.admin.storage.StorageProperties;
import com.sts.asset.entity.Asset;
import com.sts.asset.mapper.AssetMapper;
import com.sts.course.snapshot.CourseContentSnapshot;
import com.sts.course.snapshot.CourseSnapshot;
import com.sts.course.snapshot.DownloadAssetSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LearnerCourseResourceAssemblerTest {

  private AssetMapper assetMapper;
  private SysFileInfoMapper fileInfoMapper;
  private LearnerCourseResourceAssembler assembler;
  private StorageProperties storageProperties;

  @BeforeEach
  void setUp() {
    assetMapper = mock(AssetMapper.class);
    fileInfoMapper = mock(SysFileInfoMapper.class);
    storageProperties = new StorageProperties();
    storageProperties.setType("minio");
    assembler = new LearnerCourseResourceAssembler(assetMapper, fileInfoMapper, storageProperties);
  }

  @Test
  @DisplayName("ALL 与 PARTIAL 模式下冻结课程简介与可下载附件按快照顺序组装")
  void assemblesFrozenCourseAndDownloadableAsset() {
    when(assetMapper.selectBatchIds(anyCollection()))
      .thenReturn(List.of(asset(30L, 301L, 1, "SUCCEEDED", "UPLOAD")));
    when(fileInfoMapper.selectBatchIds(anyCollection()))
      .thenReturn(List.of(file(301L, "消防安全手册.pdf")));

    for (String downloadMode : List.of("ALL", "PARTIAL")) {
      CourseContentSnapshot snapshot = snapshot(downloadMode, attachment(30L, 2, 1));
      var course = assembler.toCourse(snapshot.getCourse());
      var attachments = assembler.toDownloadAssets(11L, snapshot);

      assertThat(course.getCourseName()).isEqualTo("消防安全教育");
      assertThat(course.getCoverUrl()).isEqualTo("/api/course/courses/1/cover");
      assertThat(course.getPointValue()).isEqualByComparingTo("10");
      // 学习侧四项开关必须原样投影，学员页据此决定倍速、防作弊、学时说明与水印。
      assertThat(course.getIsSpeedPlayAllowed()).isZero();
      assertThat(course.getIsStudyAnticheatEnabled()).isEqualTo(1);
      assertThat(course.getIsStudyDescriptionEnabled()).isEqualTo(1);
      assertThat(course.getIsWatermarkEnabled()).isZero();
      assertThat(attachments).hasSize(1);
      assertThat(attachments.getFirst().getFileName()).isEqualTo("消防安全手册.pdf");
      assertThat(attachments.getFirst().getDownloadable()).isTrue();
      assertThat(attachments.getFirst().getDownloadUrl())
        .isEqualTo("/api/course/learning/enrollments/11/download-assets/30/file");
      assertThat(attachments.getFirst().getUnavailableReason()).isNull();
    }
  }

  @Test
  @DisplayName("课程禁止源文件下载时返回稳定不可用原因")
  void sourceDownloadDisabled() {
    CourseContentSnapshot snapshot = snapshot("NONE", attachment(30L, 1, 1));
    when(assetMapper.selectBatchIds(anyCollection()))
      .thenReturn(List.of(asset(30L, 301L, 1, "SUCCEEDED", "UPLOAD")));
    when(fileInfoMapper.selectBatchIds(anyCollection()))
      .thenReturn(List.of(file(301L, "消防安全手册.pdf")));

    var result = assembler.toDownloadAssets(11L, snapshot).getFirst();

    assertThat(result.getDownloadable()).isFalse();
    assertThat(result.getDownloadUrl()).isNull();
    assertThat(result.getUnavailableReason()).isEqualTo("SOURCE_DOWNLOAD_DISABLED");
  }

  @Test
  @DisplayName("素材失效或处理中时分别返回不可用原因")
  void assetDisabledOrProcessing() {
    CourseContentSnapshot snapshot = snapshot("ALL",
                                              attachment(30L, 1, 1), attachment(31L, 2, 1));
    when(assetMapper.selectBatchIds(anyCollection())).thenReturn(List.of(
      asset(30L, 301L, 0, "SUCCEEDED", "UPLOAD"),
      asset(31L, 302L, 1, "PROCESSING", "UPLOAD")));
    when(fileInfoMapper.selectBatchIds(anyCollection())).thenReturn(List.of(
      file(301L, "失效.pdf"), file(302L, "处理中.mp4")));

    var result = assembler.toDownloadAssets(11L, snapshot);

    assertThat(result).extracting(item -> item.getUnavailableReason())
      .containsExactly("ASSET_DISABLED", "ASSET_PROCESSING");
  }

  @Test
  @DisplayName("源文件记录不存在时不返回下载地址")
  void fileRecordMissing() {
    CourseContentSnapshot snapshot = snapshot("ALL", attachment(30L, 1, 1));
    when(assetMapper.selectBatchIds(anyCollection()))
      .thenReturn(List.of(asset(30L, 301L, 1, "SUCCEEDED", "UPLOAD")));
    when(fileInfoMapper.selectBatchIds(anyCollection())).thenReturn(List.of());

    var result = assembler.toDownloadAssets(11L, snapshot).getFirst();

    assertThat(result.getDownloadable()).isFalse();
    assertThat(result.getUnavailableReason()).isEqualTo("FILE_NOT_FOUND");
  }

  @Test
  @DisplayName("未启用私有对象存储时不伪造下载地址")
  void storageUnavailable() {
    storageProperties.setType("local");
    CourseContentSnapshot snapshot = snapshot("ALL", attachment(30L, 1, 1));
    when(assetMapper.selectBatchIds(anyCollection()))
      .thenReturn(List.of(asset(30L, 301L, 1, "SUCCEEDED", "UPLOAD")));
    when(fileInfoMapper.selectBatchIds(anyCollection()))
      .thenReturn(List.of(file(301L, "消防安全手册.pdf")));

    var result = assembler.toDownloadAssets(11L, snapshot).getFirst();

    assertThat(result.getDownloadable()).isFalse();
    assertThat(result.getDownloadUrl()).isNull();
    assertThat(result.getUnavailableReason()).isEqualTo("STORAGE_UNAVAILABLE");

    snapshot.getCourse().setSourceDownloadMode("NONE");
    assertThat(assembler.toDownloadAssets(11L, snapshot).getFirst().getUnavailableReason())
      .isEqualTo("SOURCE_DOWNLOAD_DISABLED");
  }

  @Test
  @DisplayName("冻结附件禁用与非上传素材返回稳定不可用原因")
  void frozenAssetDisabledOrUnsupportedSource() {
    CourseContentSnapshot snapshot = snapshot("ALL",
                                              attachment(30L, 1, 0), attachment(31L, 2, 1), attachment(32L, 3, 1));
    when(assetMapper.selectBatchIds(anyCollection())).thenReturn(List.of(
      asset(30L, 301L, 1, "SUCCEEDED", "UPLOAD"),
      asset(31L, null, 1, "SUCCEEDED", "UPLOAD"),
      asset(32L, 302L, 1, "SUCCEEDED", "LINK")));
    when(fileInfoMapper.selectBatchIds(anyCollection())).thenReturn(List.of(
      file(301L, "禁用.pdf"), file(302L, "外链.pdf")));

    assertThat(assembler.toDownloadAssets(11L, snapshot))
      .extracting(item -> item.getUnavailableReason())
      .containsExactly("ASSET_DISABLED", "UNSUPPORTED_ASSET_TYPE", "UNSUPPORTED_ASSET_TYPE");
  }

  private static CourseContentSnapshot snapshot(String downloadMode, DownloadAssetSnapshot... assets) {
    CourseSnapshot course = new CourseSnapshot();
    course.setCourseId(1L);
    course.setCourseCode("SAFE-001");
    course.setCourseName("消防安全教育");
    course.setCoverFileId(900L);
    course.setIntroduction("面向员工的消防安全基础培训。");
    course.setPointValue(new BigDecimal("10"));
    course.setSourceDownloadMode(downloadMode);
    course.setIsSpeedPlayAllowed(0);
    course.setIsStudyAnticheatEnabled(1);
    course.setIsStudyDescriptionEnabled(1);
    course.setIsWatermarkEnabled(0);
    CourseContentSnapshot snapshot = new CourseContentSnapshot();
    snapshot.setCourse(course);
    snapshot.setDownloadAssets(List.of(assets));
    return snapshot;
  }

  private static DownloadAssetSnapshot attachment(Long assetId, int sortOrder, int status) {
    DownloadAssetSnapshot snapshot = new DownloadAssetSnapshot();
    snapshot.setAssetId(assetId);
    snapshot.setSortOrder(sortOrder);
    snapshot.setStatus(status);
    return snapshot;
  }

  private static Asset asset(Long assetId, Long fileId, int status, String processStatus,
                             String sourceType) {
    Asset asset = new Asset();
    asset.setAssetId(assetId);
    asset.setAssetName("附件-" + assetId);
    asset.setAssetType("DOCUMENT");
    asset.setMimeType("application/pdf");
    asset.setFileInfoId(fileId);
    asset.setStatus(status);
    asset.setProcessStatus(processStatus);
    asset.setSourceType(sourceType);
    return asset;
  }

  private static SysFileInfo file(Long fileId, String fileName) {
    SysFileInfo file = new SysFileInfo();
    file.setFileInfoId(fileId);
    file.setFileName(fileName);
    file.setFileSize(5_242_880L);
    return file;
  }
}

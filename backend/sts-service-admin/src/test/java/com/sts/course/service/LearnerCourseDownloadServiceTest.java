package com.sts.course.service;

import cn.dev33.satoken.stp.StpUtil;
import com.sts.admin.entity.SysFileInfo;
import com.sts.admin.mapper.SysFileInfoMapper;
import com.sts.admin.storage.StorageObject;
import com.sts.asset.entity.Asset;
import com.sts.asset.mapper.AssetMapper;
import com.sts.asset.service.AssetMediaAccessService;
import com.sts.common.exception.BusinessException;
import com.sts.course.entity.CourseEnrollment;
import com.sts.course.entity.CourseOffering;
import com.sts.course.entity.CourseVersion;
import com.sts.course.mapper.CourseEnrollmentMapper;
import com.sts.course.mapper.CourseOfferingMapper;
import com.sts.course.mapper.CourseVersionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.io.ByteArrayInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class LearnerCourseDownloadServiceTest {

  private static final Long USER_ID = 99L;
  private CourseEnrollmentMapper enrollmentMapper;
  private CourseOfferingMapper offeringMapper;
  private CourseVersionMapper versionMapper;
  private CourseSnapshotAssembler snapshotAssembler;
  private AssetMapper assetMapper;
  private SysFileInfoMapper fileInfoMapper;
  private AssetMediaAccessService mediaAccessService;
  private LearnerCourseDownloadService service;

  @BeforeEach
  void setUp() {
    enrollmentMapper = mock(CourseEnrollmentMapper.class);
    offeringMapper = mock(CourseOfferingMapper.class);
    versionMapper = mock(CourseVersionMapper.class);
    snapshotAssembler = new CourseSnapshotAssembler(
      mock(com.sts.course.mapper.CourseCourseCategoryMapper.class),
      mock(com.sts.course.mapper.CourseChapterMapper.class),
      mock(com.sts.course.mapper.CourseItemMapper.class),
      mock(com.sts.course.mapper.CourseDownloadAssetMapper.class),
      mock(AssetMapper.class),
      mock(CourseItemService.class), mock(com.sts.asset.service.AssetReferenceService.class),
      mock(com.sts.course.mapper.CourseItemQuestionBankMapper.class),
      mock(com.sts.question.mapper.QuestionBankMapper.class),
      mock(com.sts.paper.mapper.PaperVersionMapper.class),
      mock(com.sts.paper.mapper.PaperMapper.class), org.mockito.Mockito.mock(com.sts.course.mapper.TeacherFollowMapper.class));
    assetMapper = mock(AssetMapper.class);
    fileInfoMapper = mock(SysFileInfoMapper.class);
    mediaAccessService = mock(AssetMediaAccessService.class);
    service = new LearnerCourseDownloadService(
      enrollmentMapper, offeringMapper, versionMapper, snapshotAssembler,
      assetMapper, fileInfoMapper, mediaAccessService);
  }

  @Test
  @DisplayName("ALL 与 PARTIAL 模式下报名本人可下载冻结课程附件")
  void ownerDownloadsFrozenAttachment() {
    when(assetMapper.selectById(30L)).thenReturn(validAsset());
    when(fileInfoMapper.selectById(301L)).thenReturn(file());
    var media = new AssetMediaAccessService.MediaObject(
      new StorageObject(new ByteArrayInputStream(new byte[]{1}), 1, "application/pdf"), 1, null);
    when(mediaAccessService.sourceFile(30L, 301L, null)).thenReturn(media);

    for (String downloadMode : new String[]{"ALL", "PARTIAL"}) {
      stubContext(USER_ID, "LEARNING", "PUBLISHED", downloadMode, 30L);
      try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
        stp.when(StpUtil::getLoginIdAsLong).thenReturn(USER_ID);
        var result = service.open(11L, 30L, null);
        assertThat(result.fileName()).isEqualTo("消防安全手册.pdf");
        assertThat(result.media()).isSameAs(media);
      }
    }
  }

  @Test
  @DisplayName("非报名本人被拒绝且不读取课程或存储")
  void nonOwnerIsRejectedBeforeResourceLookup() {
    when(enrollmentMapper.selectOne(any())).thenReturn(enrollment(100L, "LEARNING"));

    try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
      stp.when(StpUtil::getLoginIdAsLong).thenReturn(USER_ID);
      assertCode(() -> service.open(11L, 30L, null), 5081);
    }

    verifyNoInteractions(offeringMapper, versionMapper, assetMapper, fileInfoMapper, mediaAccessService);
  }

  @Test
  @DisplayName("课程禁止下载时返回 5087 且不读取素材")
  void sourceDownloadDisabled() {
    stubContext(USER_ID, "LEARNING", "PUBLISHED", "NONE", 30L);
    try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
      stp.when(StpUtil::getLoginIdAsLong).thenReturn(USER_ID);
      assertCode(() -> service.open(11L, 30L, null), 5087);
    }
    verifyNoInteractions(assetMapper, fileInfoMapper, mediaAccessService);
  }

  @Test
  @DisplayName("过期报名或停用开课不允许下载")
  void invalidEnrollmentOrOfferingState() {
    stubContext(USER_ID, "EXPIRED", "PUBLISHED", "ALL", 30L);
    try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
      stp.when(StpUtil::getLoginIdAsLong).thenReturn(USER_ID);
      assertCode(() -> service.open(11L, 30L, null), 5087);
    }
    verifyNoInteractions(assetMapper, fileInfoMapper, mediaAccessService);
  }

  @Test
  @DisplayName("素材不在冻结附件中时返回 5088")
  void assetOutsideFrozenSnapshot() {
    stubContext(USER_ID, "LEARNING", "PUBLISHED", "ALL", 31L);
    try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
      stp.when(StpUtil::getLoginIdAsLong).thenReturn(USER_ID);
      assertCode(() -> service.open(11L, 30L, null), 5088);
    }
    verifyNoInteractions(assetMapper, fileInfoMapper, mediaAccessService);
  }

  @Test
  @DisplayName("文件不存在时返回 5089 且不读取存储")
  void missingFile() {
    stubContext(USER_ID, "LEARNING", "PUBLISHED", "ALL", 30L);
    when(assetMapper.selectById(30L)).thenReturn(validAsset());
    when(fileInfoMapper.selectById(301L)).thenReturn(null);
    try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
      stp.when(StpUtil::getLoginIdAsLong).thenReturn(USER_ID);
      assertCode(() -> service.open(11L, 30L, null), 5089);
    }
    verifyNoInteractions(mediaAccessService);
  }

  @Test
  @DisplayName("当前素材停用时返回 5089 且不读取文件或存储")
  void disabledAsset() {
    stubContext(USER_ID, "LEARNING", "PUBLISHED", "ALL", 30L);
    Asset asset = validAsset();
    asset.setStatus(0);
    when(assetMapper.selectById(30L)).thenReturn(asset);
    try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
      stp.when(StpUtil::getLoginIdAsLong).thenReturn(USER_ID);
      assertCode(() -> service.open(11L, 30L, null), 5089);
    }
    verifyNoInteractions(fileInfoMapper, mediaAccessService);
  }

  @Test
  @DisplayName("处理中或非 UPLOAD 素材返回 5089")
  void processingOrUnsupportedAsset() {
    Asset processing = validAsset();
    processing.setProcessStatus("PROCESSING");
    Asset unsupported = validAsset();
    unsupported.setSourceType("LINK");
    for (Asset asset : new Asset[]{processing, unsupported}) {
      reset(assetMapper, fileInfoMapper, mediaAccessService);
      stubContext(USER_ID, "LEARNING", "PUBLISHED", "ALL", 30L);
      when(assetMapper.selectById(30L)).thenReturn(asset);
      try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
        stp.when(StpUtil::getLoginIdAsLong).thenReturn(USER_ID);
        assertCode(() -> service.open(11L, 30L, null), 5089);
      }
      verifyNoInteractions(fileInfoMapper, mediaAccessService);
    }
  }

  private void stubContext(Long ownerId, String learningStatus, String offeringStatus,
                           String downloadMode, Long frozenAssetId) {
    when(enrollmentMapper.selectOne(any())).thenReturn(enrollment(ownerId, learningStatus));
    CourseOffering offering = new CourseOffering();
    offering.setCourseOfferingId(20L);
    offering.setCourseVersionId(21L);
    offering.setPublishStatus(offeringStatus);
    offering.setStatus(1);
    when(offeringMapper.selectOne(any())).thenReturn(offering);
    CourseVersion version = new CourseVersion();
    version.setCourseVersionId(21L);
    version.setContentSnapshotJson("""
                                     {"schemaVersion":1,"course":{"courseId":1,"courseName":"消防安全教育",
                                     "sourceDownloadMode":"%s"},"downloadAssets":[{"assetId":%d,"sortOrder":1,"status":1}]}
                                     """.formatted(downloadMode, frozenAssetId));
    when(versionMapper.selectById(21L)).thenReturn(version);
  }

  private static CourseEnrollment enrollment(Long ownerId, String learningStatus) {
    CourseEnrollment enrollment = new CourseEnrollment();
    enrollment.setCourseEnrollmentId(11L);
    enrollment.setCourseOfferingId(20L);
    enrollment.setUserId(ownerId);
    enrollment.setLearningStatus(learningStatus);
    return enrollment;
  }

  private static Asset validAsset() {
    Asset asset = new Asset();
    asset.setAssetId(30L);
    asset.setFileInfoId(301L);
    asset.setStatus(1);
    asset.setProcessStatus("SUCCEEDED");
    asset.setSourceType("UPLOAD");
    return asset;
  }

  private static SysFileInfo file() {
    SysFileInfo file = new SysFileInfo();
    file.setFileInfoId(301L);
    file.setFileName("消防安全手册.pdf");
    return file;
  }

  private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable action, int code) {
    assertThatThrownBy(action).isInstanceOf(BusinessException.class)
      .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(code));
  }
}

package com.sts.course.service;

import com.sts.admin.entity.SysFileInfo;
import com.sts.admin.mapper.SysFileInfoMapper;
import com.sts.admin.storage.StorageProperties;
import com.sts.asset.entity.Asset;
import com.sts.asset.mapper.AssetMapper;
import com.sts.course.snapshot.CourseContentSnapshot;
import com.sts.course.snapshot.CourseSnapshot;
import com.sts.course.snapshot.DownloadAssetSnapshot;
import com.sts.course.vo.CourseLearningCourseVO;
import com.sts.course.vo.LearnerCourseDownloadAssetVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class LearnerCourseResourceAssembler {

  private static final String PROCESS_SUCCEEDED = "SUCCEEDED";
  private static final String SOURCE_UPLOAD = "UPLOAD";

  private final AssetMapper assetMapper;
  private final SysFileInfoMapper fileInfoMapper;
  private final StorageProperties storageProperties;

  public CourseLearningCourseVO toCourse(CourseSnapshot snapshot) {
    if (snapshot == null) {
      return null;
    }
    CourseLearningCourseVO vo = new CourseLearningCourseVO();
    vo.setCourseId(snapshot.getCourseId());
    vo.setCourseCode(snapshot.getCourseCode());
    vo.setCourseName(snapshot.getCourseName());
    vo.setTeacherUserId(snapshot.getTeacherUserId());
    vo.setTeacherName(snapshot.getTeacherName());
    vo.setIntroduction(snapshot.getIntroduction());
    vo.setTeachingObjective(snapshot.getTeachingObjective());
    vo.setSyllabus(snapshot.getSyllabus());
    vo.setAssessmentMethod(snapshot.getAssessmentMethod());
    vo.setCredit(snapshot.getCredit());
    vo.setPointMode(snapshot.getPointMode());
    vo.setPointValue(snapshot.getPointValue());
    vo.setContentMode(snapshot.getContentMode());
    vo.setSourceDownloadMode(snapshot.getSourceDownloadMode());
    // 学习侧四项开关直接取冻结快照：管理端改课程基础信息后需重新发布才影响已报名学员。
    vo.setIsSpeedPlayAllowed(snapshot.getIsSpeedPlayAllowed());
    vo.setIsStudyAnticheatEnabled(snapshot.getIsStudyAnticheatEnabled());
    vo.setIsStudyDescriptionEnabled(snapshot.getIsStudyDescriptionEnabled());
    vo.setIsWatermarkEnabled(snapshot.getIsWatermarkEnabled());
    vo.setCoverUrl(courseCoverUrl(snapshot.getCourseId(), snapshot.getCoverFileId()));
    return vo;
  }

  public List<LearnerCourseDownloadAssetVO> toDownloadAssets(
    Long enrollmentId, CourseContentSnapshot snapshot) {
    List<DownloadAssetSnapshot> frozenAssets = snapshot == null || snapshot.getDownloadAssets() == null
      ? List.of() : snapshot.getDownloadAssets();
    if (frozenAssets.isEmpty()) {
      return List.of();
    }

    List<Long> assetIds = frozenAssets.stream().map(DownloadAssetSnapshot::getAssetId)
      .filter(java.util.Objects::nonNull).distinct().toList();
    Map<Long, Asset> assets = assetIds.isEmpty() ? Collections.emptyMap()
      : assetMapper.selectBatchIds(assetIds).stream().collect(Collectors.toMap(
      Asset::getAssetId, Function.identity(), (first, ignored) -> first));
    List<Long> fileIds = assets.values().stream().map(Asset::getFileInfoId)
      .filter(java.util.Objects::nonNull).distinct().toList();
    Map<Long, SysFileInfo> files = fileIds.isEmpty() ? Collections.emptyMap()
      : fileInfoMapper.selectBatchIds(fileIds).stream().collect(Collectors.toMap(
      SysFileInfo::getFileInfoId, Function.identity(), (first, ignored) -> first));

    String downloadMode = snapshot.getCourse() == null
      ? null : snapshot.getCourse().getSourceDownloadMode();
    return frozenAssets.stream()
      .map(frozen -> toDownloadAsset(enrollmentId, downloadMode, frozen, assets, files))
      .toList();
  }

  private LearnerCourseDownloadAssetVO toDownloadAsset(
    Long enrollmentId, String downloadMode, DownloadAssetSnapshot frozen,
    Map<Long, Asset> assets, Map<Long, SysFileInfo> files) {
    Asset asset = assets.get(frozen.getAssetId());
    SysFileInfo file = asset == null ? null : files.get(asset.getFileInfoId());
    LearnerCourseDownloadAssetVO vo = new LearnerCourseDownloadAssetVO();
    vo.setAssetId(frozen.getAssetId());
    vo.setSortOrder(frozen.getSortOrder());
    if (asset != null) {
      vo.setAssetName(asset.getAssetName());
      vo.setAssetType(asset.getAssetType());
      vo.setMimeType(asset.getMimeType());
      vo.setFileInfoId(asset.getFileInfoId());
    }
    if (file != null) {
      vo.setFileName(file.getFileName());
      vo.setFileSize(file.getFileSize());
    }

    String unavailableReason = unavailableReason(downloadMode, frozen, asset, file);
    boolean downloadable = unavailableReason == null;
    vo.setDownloadable(downloadable);
    vo.setUnavailableReason(unavailableReason);
    if (downloadable) {
      vo.setDownloadUrl("/api/course/learning/enrollments/" + enrollmentId
                          + "/download-assets/" + frozen.getAssetId() + "/file");
    }
    return vo;
  }

  private String unavailableReason(String downloadMode, DownloadAssetSnapshot frozen,
                                   Asset asset, SysFileInfo file) {
    if (!CourseSourceDownloadPolicy.isEnabled(downloadMode)) {
      return "SOURCE_DOWNLOAD_DISABLED";
    }
    if (!Integer.valueOf(1).equals(frozen.getStatus())) {
      return "ASSET_DISABLED";
    }
    if (asset == null || !Integer.valueOf(1).equals(asset.getStatus())) {
      return "ASSET_DISABLED";
    }
    if (!PROCESS_SUCCEEDED.equals(asset.getProcessStatus())) {
      return "ASSET_PROCESSING";
    }
    if (!SOURCE_UPLOAD.equals(asset.getSourceType()) || asset.getFileInfoId() == null) {
      return "UNSUPPORTED_ASSET_TYPE";
    }
    if (file == null) {
      return "FILE_NOT_FOUND";
    }
    if (!"minio".equals(storageProperties.getType())) {
      return "STORAGE_UNAVAILABLE";
    }
    return null;
  }

  static String courseCoverUrl(Long courseId, Long coverFileId) {
    return courseId == null || coverFileId == null ? null : "/api/course/courses/" + courseId + "/cover";
  }
}

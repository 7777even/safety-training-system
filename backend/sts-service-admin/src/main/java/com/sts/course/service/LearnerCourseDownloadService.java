package com.sts.course.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sts.admin.entity.SysFileInfo;
import com.sts.admin.mapper.SysFileInfoMapper;
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
import com.sts.course.snapshot.CourseContentSnapshot;
import com.sts.course.snapshot.DownloadAssetSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "storage.type", havingValue = "minio")
public class LearnerCourseDownloadService {

  private static final Set<String> DOWNLOADABLE_ENROLLMENT_STATUSES =
    Set.of("NOT_STARTED", "LEARNING", "COMPLETED");
  private static final Set<String> DOWNLOADABLE_OFFERING_STATUSES = Set.of("PUBLISHED", "ENDED");

  private final CourseEnrollmentMapper enrollmentMapper;
  private final CourseOfferingMapper offeringMapper;
  private final CourseVersionMapper versionMapper;
  private final CourseSnapshotAssembler snapshotAssembler;
  private final AssetMapper assetMapper;
  private final SysFileInfoMapper fileInfoMapper;
  private final AssetMediaAccessService mediaAccessService;

  public DownloadFile open(Long enrollmentId, Long assetId, String rangeHeader) {
    CourseEnrollment enrollment = enrollmentMapper.selectOne(
      new LambdaQueryWrapper<CourseEnrollment>()
        .eq(CourseEnrollment::getCourseEnrollmentId, enrollmentId));
    if (enrollment == null) {
      throw new BusinessException(HttpStatus.NOT_FOUND, 5071, "报名不存在");
    }
    if (!Long.valueOf(StpUtil.getLoginIdAsLong()).equals(enrollment.getUserId())) {
      throw new BusinessException(HttpStatus.FORBIDDEN, 5081, "无权访问该报名学习数据");
    }
    if (!DOWNLOADABLE_ENROLLMENT_STATUSES.contains(enrollment.getLearningStatus())) {
      throw downloadForbidden("当前报名状态不允许下载附件");
    }

    CourseOffering offering = offeringMapper.selectOne(
      new LambdaQueryWrapper<CourseOffering>()
        .eq(CourseOffering::getCourseOfferingId, enrollment.getCourseOfferingId()));
    if (offering == null) {
      throw new BusinessException(HttpStatus.NOT_FOUND, 5061, "开课不存在");
    }
    if (!Integer.valueOf(1).equals(offering.getStatus())
      || !DOWNLOADABLE_OFFERING_STATUSES.contains(offering.getPublishStatus())) {
      throw downloadForbidden("当前开课状态不允许下载附件");
    }

    CourseContentSnapshot snapshot = loadSnapshot(offering);
    if (snapshot.getCourse() == null
      || !CourseSourceDownloadPolicy.isEnabled(snapshot.getCourse().getSourceDownloadMode())) {
      throw downloadForbidden("课程不允许下载源文件");
    }
    DownloadAssetSnapshot frozenAsset = snapshot.getDownloadAssets() == null ? null
      : snapshot.getDownloadAssets().stream()
      .filter(item -> assetId.equals(item.getAssetId())).findFirst().orElse(null);
    if (frozenAsset == null) {
      throw new BusinessException(HttpStatus.NOT_FOUND, 5088, "素材不属于该课程冻结附件");
    }
    if (!Integer.valueOf(1).equals(frozenAsset.getStatus())) {
      throw downloadForbidden("课程附件已停用");
    }

    Asset asset = assetMapper.selectById(assetId);
    if (!isDownloadableAsset(asset)) {
      throw unavailable();
    }
    SysFileInfo file = fileInfoMapper.selectById(asset.getFileInfoId());
    if (file == null) {
      throw unavailable();
    }
    try {
      return new DownloadFile(file.getFileName(),
                              mediaAccessService.sourceFile(assetId, asset.getFileInfoId(), rangeHeader));
    } catch (BusinessException e) {
      if (e.getCode() == 6011) {
        throw unavailable();
      }
      throw e;
    }
  }

  private CourseContentSnapshot loadSnapshot(CourseOffering offering) {
    if (offering.getCourseVersionId() == null) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 5083, "开课未绑定课程版本快照");
    }
    CourseVersion version = versionMapper.selectById(offering.getCourseVersionId());
    if (version == null || version.getContentSnapshotJson() == null) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 5083, "课程版本快照不存在");
    }
    return snapshotAssembler.parseContentSnapshot(version.getContentSnapshotJson());
  }

  private boolean isDownloadableAsset(Asset asset) {
    return asset != null && Integer.valueOf(1).equals(asset.getStatus())
      && "SUCCEEDED".equals(asset.getProcessStatus()) && "UPLOAD".equals(asset.getSourceType())
      && asset.getFileInfoId() != null;
  }

  private static BusinessException downloadForbidden(String message) {
    return new BusinessException(HttpStatus.BAD_REQUEST, 5087, message);
  }

  private static BusinessException unavailable() {
    return new BusinessException(HttpStatus.NOT_FOUND, 5089, "附件素材或源文件当前不可下载");
  }

  public record DownloadFile(String fileName, AssetMediaAccessService.MediaObject media) {
  }
}

package com.sts.course.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sts.asset.service.AssetReferenceService;
import com.sts.common.exception.BusinessException;
import com.sts.common.service.BaseServiceImpl;
import com.sts.course.converter.CourseDownloadAssetConverter;
import com.sts.course.dto.DownloadAssetReplaceRequest;
import com.sts.course.entity.Course;
import com.sts.course.entity.CourseDownloadAsset;
import com.sts.course.mapper.CourseDownloadAssetMapper;
import com.sts.course.mapper.CourseMapper;
import com.sts.course.vo.DownloadAssetVO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CourseDownloadAssetServiceImpl extends BaseServiceImpl<CourseDownloadAssetMapper, CourseDownloadAsset>
  implements CourseDownloadAssetService {

  private final CourseDownloadAssetMapper downloadAssetMapper;
  private final CourseDownloadAssetConverter downloadAssetConverter;
  private final CourseMapper courseMapper;
  private final AssetReferenceService assetReferenceService;

  @Override
  public List<DownloadAssetVO> listByCourse(Long courseId) {
    requireCourse(courseId);
    return downloadAssetMapper.selectList(
        new LambdaQueryWrapper<CourseDownloadAsset>()
          .eq(CourseDownloadAsset::getCourseId, courseId)
          .orderByAsc(CourseDownloadAsset::getSortOrder)
          .orderByAsc(CourseDownloadAsset::getCourseDownloadAssetId))
      .stream().map(downloadAssetConverter::toVO).toList();
  }

  @Override
  @Transactional
  public void replaceAll(Long courseId, DownloadAssetReplaceRequest request) {
    requireCourse(courseId);
    Set<Long> assetIds = new HashSet<>();
    for (DownloadAssetReplaceRequest.DownloadAssetEntry entry : request.getAssets()) {
      if (!assetIds.add(entry.getAssetId())) {
        throw new BusinessException(HttpStatus.BAD_REQUEST, 5038, "下载素材列表中存在重复素材");
      }
      assetReferenceService.requireUsableAsset(entry.getAssetId());
    }
    List<CourseDownloadAsset> existing = downloadAssetMapper.selectList(
      new LambdaQueryWrapper<CourseDownloadAsset>().eq(CourseDownloadAsset::getCourseId, courseId));
    for (CourseDownloadAsset row : existing) {
      this.removeById(row.getCourseDownloadAssetId());
    }
    for (DownloadAssetReplaceRequest.DownloadAssetEntry entry : request.getAssets()) {
      CourseDownloadAsset entity = downloadAssetConverter.toEntity(entry);
      entity.setCourseId(courseId);
      downloadAssetMapper.insert(entity);
    }
  }

  @Override
  @Transactional
  public void deleteAllOfCourse(Long courseId) {
    List<CourseDownloadAsset> rows = downloadAssetMapper.selectList(
      new LambdaQueryWrapper<CourseDownloadAsset>().eq(CourseDownloadAsset::getCourseId, courseId));
    for (CourseDownloadAsset row : rows) {
      this.removeById(row.getCourseDownloadAssetId());
    }
  }

  private void requireCourse(Long courseId) {
    Course course = courseMapper.selectOne(
      new LambdaQueryWrapper<Course>()
        .eq(Course::getCourseId, courseId));
    if (course == null) {
      throw new BusinessException(HttpStatus.NOT_FOUND, 5011, "课程不存在");
    }
  }
}

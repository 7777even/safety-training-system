package com.sts.course.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sts.course.entity.CourseDownloadAsset;
import com.sts.course.entity.CourseItem;
import com.sts.course.mapper.CourseDownloadAssetMapper;
import com.sts.course.mapper.CourseItemMapper;
import com.sts.course.vo.CourseAssetReferenceVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CourseAssetReferenceServiceImpl implements CourseAssetReferenceService {

  private final CourseItemMapper itemMapper;
  private final CourseDownloadAssetMapper downloadAssetMapper;

  @Override
  public CourseAssetReferenceVO getReferences(Long assetId) {
    Long itemCount = itemMapper.selectCount(
      new LambdaQueryWrapper<CourseItem>().eq(CourseItem::getAssetId, assetId));
    Long downloadCount = downloadAssetMapper.selectCount(
      new LambdaQueryWrapper<CourseDownloadAsset>().eq(CourseDownloadAsset::getAssetId, assetId));
    long items = itemCount != null ? itemCount : 0L;
    long downloads = downloadCount != null ? downloadCount : 0L;

    CourseAssetReferenceVO vo = new CourseAssetReferenceVO();
    vo.setAssetId(assetId);
    vo.setItemCount(items);
    vo.setDownloadCount(downloads);
    vo.setReferenceCount(items + downloads);
    return vo;
  }
}

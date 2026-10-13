package com.sts.course.converter;

import com.sts.course.dto.DownloadAssetReplaceRequest;
import com.sts.course.entity.CourseDownloadAsset;
import com.sts.course.vo.DownloadAssetVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface CourseDownloadAssetConverter {

  DownloadAssetVO toVO(CourseDownloadAsset entity);

  @Mapping(target = "courseId", ignore = true)
  CourseDownloadAsset toEntity(DownloadAssetReplaceRequest.DownloadAssetEntry entry);
}

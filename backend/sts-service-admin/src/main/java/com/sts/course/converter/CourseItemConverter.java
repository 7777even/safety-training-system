package com.sts.course.converter;

import com.sts.course.dto.ItemCreateRequest;
import com.sts.course.dto.ItemMoveRequest;
import com.sts.course.dto.ItemUpdateRequest;
import com.sts.course.entity.CourseItem;
import com.sts.course.vo.CourseItemVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface CourseItemConverter {

  CourseItemVO toVO(CourseItem item);

  @Mapping(target = "courseId", ignore = true)
  CourseItem toEntity(ItemCreateRequest request);

  @Mapping(target = "version", ignore = true)
  @Mapping(target = "assetId", ignore = true)
  @Mapping(target = "paperVersionId", ignore = true)
  void updateEntity(@MappingTarget CourseItem entity, ItemUpdateRequest request);

  @Mapping(target = "version", ignore = true)
  void applyMove(@MappingTarget CourseItem entity, ItemMoveRequest request);
}

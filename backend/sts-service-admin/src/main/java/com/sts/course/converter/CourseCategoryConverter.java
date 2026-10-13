package com.sts.course.converter;

import com.sts.course.dto.CategoryCreateRequest;
import com.sts.course.dto.CategoryUpdateRequest;
import com.sts.course.entity.CourseCategory;
import com.sts.course.vo.CourseCategoryTreeVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface CourseCategoryConverter {

  // ======================== Entity → VO ========================

  @Mapping(target = "children", ignore = true)
  CourseCategoryTreeVO toTreeNode(CourseCategory category);

  // ======================== DTO → Entity ========================

  CourseCategory toEntity(CategoryCreateRequest request);

  @Mapping(target = "version", ignore = true)
  void updateEntity(@MappingTarget CourseCategory entity, CategoryUpdateRequest request);
}

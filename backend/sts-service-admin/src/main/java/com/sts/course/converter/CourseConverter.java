package com.sts.course.converter;

import com.sts.course.dto.CourseCreateRequest;
import com.sts.course.dto.CourseUpdateRequest;
import com.sts.course.entity.Course;
import com.sts.course.vo.CourseListItemVO;
import com.sts.course.vo.CourseVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface CourseConverter {

  // ======================== Entity → VO ========================

  @Mapping(target = "categoryIds", ignore = true)
  CourseVO toVO(Course course);

  CourseListItemVO toListItem(Course course);

  // ======================== DTO → Entity ========================

  Course toEntity(CourseCreateRequest request);

  @Mapping(target = "version", ignore = true)
  void updateEntity(@MappingTarget Course entity, CourseUpdateRequest request);
}

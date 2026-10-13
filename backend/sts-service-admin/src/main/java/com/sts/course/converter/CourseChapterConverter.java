package com.sts.course.converter;

import com.sts.course.dto.ChapterCreateRequest;
import com.sts.course.dto.ChapterUpdateRequest;
import com.sts.course.entity.CourseChapter;
import com.sts.course.vo.CourseChapterVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface CourseChapterConverter {

  // ======================== Entity → VO ========================

  CourseChapterVO toVO(CourseChapter chapter);

  // ======================== DTO → Entity ========================

  CourseChapter toEntity(ChapterCreateRequest request);

  @Mapping(target = "version", ignore = true)
  void updateEntity(@MappingTarget CourseChapter entity, ChapterUpdateRequest request);
}

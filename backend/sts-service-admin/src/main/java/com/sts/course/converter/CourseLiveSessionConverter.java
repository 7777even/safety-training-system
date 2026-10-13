package com.sts.course.converter;

import com.sts.course.dto.LiveSessionCreatePayload;
import com.sts.course.dto.LiveSessionUpdateRequest;
import com.sts.course.entity.CourseLiveSession;
import com.sts.course.vo.LiveSessionVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface CourseLiveSessionConverter {

  LiveSessionVO toVO(CourseLiveSession session);

  @Mapping(target = "organizationId", ignore = true)
  @Mapping(target = "courseItemId", ignore = true)
  @Mapping(target = "liveStatus", constant = "DRAFT")
  @Mapping(target = "status", constant = "1")
  CourseLiveSession toEntity(LiveSessionCreatePayload payload);

  @Mapping(target = "version", ignore = true)
  void updateEntity(@MappingTarget CourseLiveSession entity, LiveSessionUpdateRequest request);
}

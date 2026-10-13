package com.sts.admin.system.converter;

import com.sts.admin.entity.SysConfig;
import com.sts.admin.system.dto.ConfigCreateRequest;
import com.sts.admin.system.dto.ConfigUpdateRequest;
import com.sts.admin.system.vo.ConfigVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface SysConfigConverter {

    // ======================== Entity → VO ========================

    ConfigVO toVO(SysConfig config);

    // ======================== DTO → Entity ========================

    SysConfig toEntity(ConfigCreateRequest request);

    @Mapping(target = "version", ignore = true)
    void updateEntity(@MappingTarget SysConfig entity, ConfigUpdateRequest request);
}

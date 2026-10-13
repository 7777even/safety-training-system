package com.sts.admin.system.converter;

import com.sts.admin.entity.SysRole;
import com.sts.admin.system.dto.RoleCreateRequest;
import com.sts.admin.system.dto.RoleUpdateRequest;
import com.sts.admin.system.vo.RoleVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface SysRoleConverter {

    // ======================== Entity → VO ========================

    @Mapping(target = "menuIds", ignore = true)
    RoleVO toVO(SysRole role);

    // ======================== DTO → Entity ========================

    SysRole toEntity(RoleCreateRequest request);

    @Mapping(target = "version", ignore = true)
    void updateEntity(@MappingTarget SysRole entity, RoleUpdateRequest request);
}

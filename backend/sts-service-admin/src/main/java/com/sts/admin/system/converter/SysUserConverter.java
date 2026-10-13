package com.sts.admin.system.converter;

import com.sts.admin.entity.SysUser;
import com.sts.admin.system.dto.UserCreateRequest;
import com.sts.admin.system.dto.UserUpdateRequest;
import com.sts.admin.system.vo.UserVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface SysUserConverter {

    // ======================== Entity → VO ========================

    @Mapping(target = "roleIds", ignore = true)
    @Mapping(target = "tagIds", ignore = true)
    @Mapping(target = "positionIds", ignore = true)
    @Mapping(target = "primaryPositionId", ignore = true)
    @Mapping(target = "deptName", ignore = true)
    @Mapping(target = "createByName", ignore = true)
    UserVO toVO(SysUser user);

    // ======================== DTO → Entity ========================

  @Mapping(target = "organizationId", ignore = true)
    SysUser toEntity(UserCreateRequest request);

    @Mapping(target = "version", ignore = true)
    @Mapping(target = "organizationId", ignore = true)
    void updateEntity(@MappingTarget SysUser entity, UserUpdateRequest request);
}

package com.sts.admin.system.converter;

import com.sts.admin.entity.SysDept;
import com.sts.admin.system.dto.DeptCreateRequest;
import com.sts.admin.system.dto.DeptUpdateRequest;
import com.sts.admin.system.vo.DeptTreeVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface SysDeptConverter {

    // ======================== Entity → VO ========================

    @Mapping(target = "children", ignore = true)
    DeptTreeVO toTreeNode(SysDept dept);

    // ======================== DTO → Entity ========================

    SysDept toEntity(DeptCreateRequest request);

    @Mapping(target = "version", ignore = true)
    void updateEntity(@MappingTarget SysDept entity, DeptUpdateRequest request);
}

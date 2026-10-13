package com.sts.admin.system.converter;

import com.sts.admin.entity.SysMenu;
import com.sts.admin.system.dto.MenuCreateRequest;
import com.sts.admin.system.dto.MenuUpdateRequest;
import com.sts.admin.system.vo.MenuTreeVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface SysMenuConverter {

    // ======================== Entity → VO ========================

    @Mapping(target = "children", ignore = true)
    MenuTreeVO toTreeNode(SysMenu menu);

  SysMenu toEntity(MenuCreateRequest request);

  void updateEntity(@org.mapstruct.MappingTarget SysMenu menu, MenuUpdateRequest request);
}

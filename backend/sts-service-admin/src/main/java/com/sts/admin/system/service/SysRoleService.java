package com.sts.admin.system.service;

import com.sts.admin.system.dto.RoleCreateRequest;
import com.sts.admin.system.dto.RoleQuery;
import com.sts.admin.system.dto.RoleUpdateRequest;
import com.sts.admin.system.vo.RoleVO;
import com.sts.common.api.PageRequest;
import com.sts.common.api.PageResult;

public interface SysRoleService {

    PageResult<RoleVO> pageRoles(PageRequest<RoleQuery> request);

    RoleVO getRole(Long roleId);

    Long createRole(RoleCreateRequest request);

    void updateRole(Long roleId, RoleUpdateRequest request);

    void deleteRole(Long roleId);
}

package com.sts.admin.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.sts.admin.aop.annotation.BusinessType;
import com.sts.admin.aop.annotation.OperLog;
import com.sts.admin.system.dto.RoleCreateRequest;
import com.sts.admin.system.dto.RoleQuery;
import com.sts.admin.system.dto.RoleUpdateRequest;
import com.sts.admin.system.service.SysRoleService;
import com.sts.admin.system.vo.RoleVO;
import com.sts.common.api.PageRequest;
import com.sts.common.api.PageResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 系统角色及角色授权信息管理接口。
 */
@RestController
@RequestMapping("/system/roles")
@RequiredArgsConstructor
public class SysRoleController {

    private final SysRoleService roleService;

  /**
   * 查询角色列表。
   */
    @GetMapping
    @SaCheckPermission("sys:role:list")
    public PageResult<RoleVO> listRoles(PageRequest<RoleQuery> request) {
        return roleService.pageRoles(request);
    }

  /**
   * 查询角色。
   */
  @GetMapping("/{roleId}")
    @SaCheckPermission("sys:role:query")
    public RoleVO getRole(@PathVariable Long roleId) {
        return roleService.getRole(roleId);
    }

    /**
     * 创建角色。
     */
    @OperLog(title = "角色管理", businessType = BusinessType.INSERT)
    @PostMapping
    @SaCheckPermission("sys:role:add")
    public Long createRole(@RequestBody @Valid RoleCreateRequest request) {
        return roleService.createRole(request);
    }

    /**
     * 更新角色。
     */
    @OperLog(title = "角色管理", businessType = BusinessType.UPDATE)
    @PutMapping("/{roleId}")
    @SaCheckPermission("sys:role:edit")
    public void updateRole(@PathVariable Long roleId, @RequestBody @Valid RoleUpdateRequest request) {
        roleService.updateRole(roleId, request);
    }

    /**
     * 删除角色。
     */
    @OperLog(title = "角色管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{roleId}")
    @SaCheckPermission("sys:role:delete")
    public void deleteRole(@PathVariable Long roleId) {
        roleService.deleteRole(roleId);
    }
}

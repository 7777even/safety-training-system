package com.sts.admin.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.sts.admin.aop.annotation.BusinessType;
import com.sts.admin.aop.annotation.OperLog;
import com.sts.admin.system.dto.MenuBatchVisibilityRequest;
import com.sts.admin.system.dto.MenuCreateRequest;
import com.sts.admin.system.dto.MenuUpdateRequest;
import com.sts.admin.system.dto.MenuVisibilityRequest;
import com.sts.admin.system.service.SysMenuService;
import com.sts.admin.system.vo.MenuTreeVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 系统菜单树、菜单信息及可见性管理接口。
 */
@RestController
@RequestMapping("/system/menus")
@RequiredArgsConstructor
public class SysMenuController {

    private final SysMenuService menuService;

  /**
   * 查询菜单树。
   */
    @GetMapping("/tree")
    @SaCheckPermission("sys:menu:list")
    public List<MenuTreeVO> getMenuTree(
        @RequestParam(required = false) String menuName,
        @RequestParam(required = false) String perms,
        @RequestParam(required = false) Integer isVisible) {
        return menuService.getMenuTree(menuName, perms, isVisible);
    }

  /**
   * 创建菜单。
   */
  @PostMapping
  @SaCheckPermission("sys:menu:add")
  @OperLog(title = "菜单管理", businessType = BusinessType.INSERT)
  public Long createMenu(@RequestBody @Valid MenuCreateRequest request) {
    return menuService.createMenu(request);
  }

  /**
   * 更新菜单。
   */
  @PutMapping("/{menuId}")
  @SaCheckPermission("sys:menu:edit")
  @OperLog(title = "菜单管理", businessType = BusinessType.UPDATE)
  public void updateMenu(@PathVariable Long menuId, @RequestBody @Valid MenuUpdateRequest request) {
    menuService.updateMenu(menuId, request);
  }

  /**
   * 删除菜单。
   */
  @DeleteMapping("/{menuId}")
  @SaCheckPermission("sys:menu:delete")
  @OperLog(title = "菜单管理", businessType = BusinessType.DELETE)
  public void deleteMenu(@PathVariable Long menuId) {
    menuService.deleteMenu(menuId);
    }

  /**
   * 更新菜单可见性。
   */
  @PutMapping("/{menuId}/visibility")
    @SaCheckPermission("sys:menu:edit")
    @OperLog(title = "菜单管理", businessType = BusinessType.UPDATE)
    public void updateVisibility(@PathVariable Long menuId, @RequestBody @Valid MenuVisibilityRequest request) {
        menuService.updateVisibility(menuId, request.getIsVisible(), request.getVersion());
    }

    /**
     * 批量更新菜单可见性。
     */
    @PutMapping("/batch-visibility")
    @SaCheckPermission("sys:menu:edit")
    @OperLog(title = "菜单管理", businessType = BusinessType.UPDATE)
    public void batchUpdateVisibility(@RequestBody @Valid MenuBatchVisibilityRequest request) {
        menuService.batchUpdateVisibility(request.getVisibleIds(), request.getItems());
    }
}

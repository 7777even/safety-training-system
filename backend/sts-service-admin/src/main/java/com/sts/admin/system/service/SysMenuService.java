package com.sts.admin.system.service;

import com.sts.admin.system.dto.MenuBatchVisibilityRequest;
import com.sts.admin.system.dto.MenuCreateRequest;
import com.sts.admin.system.dto.MenuUpdateRequest;
import com.sts.admin.system.vo.MenuTreeVO;

import java.util.List;

public interface SysMenuService {

    List<MenuTreeVO> getMenuTree(String menuName, String perms, Integer isVisible);

  Long createMenu(MenuCreateRequest request);

  void updateMenu(Long menuId, MenuUpdateRequest request);

  void deleteMenu(Long menuId);

    /** 侧边栏菜单树（仅 M/C 类型 + 可见） */
    List<MenuTreeVO> getSidebarMenuTree();

    /**
     * 👑 仅保留可见性修改功能
     */
    void updateVisibility(Long menuId, Integer isVisible, Integer version);

    /**
     * 👑 批量更新可见性
     */
    void batchUpdateVisibility(List<Long> visibleIds, List<MenuBatchVisibilityRequest.MenuVersionItem> items);
}

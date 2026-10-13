package com.sts.admin.system.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sts.admin.entity.SysMenu;
import com.sts.admin.entity.SysRoleMenu;
import com.sts.admin.mapper.SysMenuMapper;
import com.sts.admin.mapper.SysRoleMenuMapper;
import com.sts.admin.system.converter.SysMenuConverter;
import com.sts.admin.system.dto.MenuBatchVisibilityRequest;
import com.sts.admin.system.dto.MenuCreateRequest;
import com.sts.admin.system.dto.MenuUpdateRequest;
import com.sts.admin.system.vo.MenuTreeVO;
import com.sts.admin.tenant.TenantSessionKeys;
import com.sts.common.exception.BusinessException;
import com.sts.common.service.BaseServiceImpl;
import com.sts.org.service.TenantDirectory;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SysMenuServiceImpl extends BaseServiceImpl<SysMenuMapper, SysMenu> implements SysMenuService {

    /**
     * 仅监管平台租户会话可见的模块：园区监管。
     * 模块 ID 定义见 {@code V20260929120000__add_m5_pc_menus.sql}。
     */
    private static final Set<Long> REGULATOR_ONLY_MODULES = Set.of(9810L);

    /**
     * 仅受监管业务租户（企业、培训机构）会话可见的模块：企业工作台。
     * 模块 ID 定义见 {@code V20260929120000__add_m5_pc_menus.sql}。
     */
    private static final Set<Long> NON_REGULATOR_ONLY_MODULES = Set.of(9800L);

    private final SysMenuMapper menuMapper;
  private final SysRoleMenuMapper roleMenuMapper;
    private final SysMenuConverter menuConverter;

    @Override
    public List<MenuTreeVO> getMenuTree(String menuName, String perms, Integer isVisible) {
        LambdaQueryWrapper<SysMenu> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(menuName), SysMenu::getMenuName, menuName)
            .like(StringUtils.hasText(perms), SysMenu::getPerms, perms)
            .eq(isVisible != null, SysMenu::getIsVisible, isVisible)
            .orderByAsc(SysMenu::getSortOrder)
            .orderByAsc(SysMenu::getMenuId);
        List<SysMenu> menus = menuMapper.selectList(wrapper);
        return buildTree(menus);
    }

  @Override
  @Transactional
  public Long createMenu(MenuCreateRequest request) {
    validateParent(request.getParentId(), null);
    validateVisibleParent(request.getParentId(), request.getIsVisible());
    SysMenu menu = menuConverter.toEntity(request);
    menuMapper.insert(menu);
    return menu.getMenuId();
  }

  @Override
  @Transactional
  public void updateMenu(Long menuId, MenuUpdateRequest request) {
    SysMenu menu = requireMenu(menuId);
    validateParent(request.getParentId(), menuId);
    assertNotMoveToDescendant(menuId, request.getParentId());
    validateVisibleParent(request.getParentId(), request.getIsVisible());

    menu.setVersion(request.getVersion());
    menuConverter.updateEntity(menu, request);
    menuMapper.updateByIdWithVersionCheck(menu);
  }

  @Override
  @Transactional
  public void deleteMenu(Long menuId) {
    requireMenu(menuId);
    Long childCount = menuMapper.selectCount(
      new LambdaQueryWrapper<SysMenu>().eq(SysMenu::getParentId, menuId));
    if (childCount != null && childCount > 0) {
      throw new BusinessException(HttpStatus.CONFLICT, 2027, "存在子菜单，无法删除");
    }
    Long roleBindingCount = roleMenuMapper.countLiveRoleBindingsByMenuId(menuId);
    if (roleBindingCount != null && roleBindingCount > 0) {
      throw new BusinessException(HttpStatus.CONFLICT, 2028, "菜单已被角色关联，无法删除");
    }
    // 角色已删或不存在时留下的孤儿关联不再阻断删除。
    roleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getMenuId, menuId));
    this.removeById(menuId);
  }

    @Override
    public List<MenuTreeVO> getSidebarMenuTree() {
        Long userId = StpUtil.getLoginIdAsLong();

        List<SysMenu> menus;
        if (StpUtil.hasPermission("*")) {
            // 👑 超级管理员：拉取所有类型的菜单（D, M, C, F）
            LambdaQueryWrapper<SysMenu> wrapper = new LambdaQueryWrapper<>();
            wrapper.orderByAsc(SysMenu::getSortOrder)
                .orderByAsc(SysMenu::getMenuId);
            menus = menuMapper.selectList(wrapper);
            // 专属模块按会话租户类型收敛：侧边栏菜单以本接口为唯一数据源，在此剔除可让
            // PC 与移动端同时生效。不用 SQL 条件是因为 module_id 可为 NULL，NOT IN 会因
            // 三值逻辑把这类菜单整行排除；moduleId 判空前置于 contains 之前，避免
            // Set.of 的 contains(null) 抛 NPE。
            Set<Long> hiddenModules = hiddenModuleIds(
                StpUtil.getTokenSession().getString(TenantSessionKeys.ORG_TYPE));
            menus = menus.stream()
                .filter(menu -> menu.getModuleId() == null
                    || !hiddenModules.contains(menu.getModuleId()))
                .toList();
        } else {
            // 普通用户：根据权限拉取
            menus = menuMapper.selectAccessibleMenusByUserId(userId);
        }

        return buildTree(menus);
    }

    /**
     * 计算当前会话租户类型下必须从侧边栏隐藏的专属模块。
     * <p>
     * 专属模块只服务于对应租户类型：监管平台租户看不到企业工作台，企业/培训机构租户
     * 看不到园区监管。判据是会话租户类型而非账号身份——{@code superAdmin} 的 {@code "*"}
     * 只代表功能特权，不改变会话的数据范围，以企业编码登录时打开监管入口会被
     * {@code RegulatorIdentityGuard} 拒为 4065，入口不应先亮出来。会话缺少租户类型时
     * 按非监管处理，即不显示监管入口。
     *
     * @param orgType 会话中的租户类型，可为 null
     * @return 需要隐藏的模块 ID；调用方必须先判空 moduleId 再调用 {@code contains}
     */
    static Set<Long> hiddenModuleIds(String orgType) {
        return TenantDirectory.TYPE_REGULATOR.equals(orgType)
            ? NON_REGULATOR_ONLY_MODULES
            : REGULATOR_ONLY_MODULES;
    }

    @Override
    @Transactional
    public void updateVisibility(Long menuId, Integer isVisible, Integer version) {
        SysMenu menu = requireMenu(menuId);
        menu.setVersion(version);
        menu.setIsVisible(isVisible);

        // 👑 核心逻辑：父不可见，子就一定不可见
        if (isVisible != null && isVisible == 1) {
            // 如果尝试设置为可见，必须检查父级是否可见
            if (menu.getParentId() != null && menu.getParentId() != 0L) {
                SysMenu parent = menuMapper.selectById(menu.getParentId());
                if (parent != null && parent.getIsVisible() == 0) {
                    throw new BusinessException(HttpStatus.BAD_REQUEST, 2011, "上级菜单已禁用，无法将子项设为启用");
                }
            }
        }

        menuMapper.updateByIdWithVersionCheck(menu);

        // 如果当前节点被禁用，递归禁用所有子节点
        if (isVisible != null && isVisible == 0) {
            cascadeHideChildren(menuId);
        }
    }

    @Override
    @Transactional
    public void batchUpdateVisibility(List<Long> visibleIds, List<MenuBatchVisibilityRequest.MenuVersionItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }

        Set<Long> visibleSet = new HashSet<>(visibleIds);

        for (MenuBatchVisibilityRequest.MenuVersionItem item : items) {
            SysMenu menu = new SysMenu();
            menu.setMenuId(item.getMenuId());
            menu.setVersion(item.getVersion());
            menu.setIsVisible(visibleSet.contains(item.getMenuId()) ? 1 : 0);

            // 👑 批量更新，由于前端已经根据树形逻辑计算好了可见性集合（父子联动），
            // 这里直接根据集合状态更新即可，不需要再执行 cascadeHideChildren。
            menuMapper.updateByIdWithVersionCheck(menu);
        }
    }

    private void cascadeHideChildren(Long parentId) {
        List<SysMenu> children = menuMapper.selectList(new LambdaQueryWrapper<SysMenu>()
                                                           .eq(SysMenu::getParentId, parentId)
                                                           .eq(SysMenu::getIsVisible, 1)); // 仅处理当前可见的子项

        for (SysMenu child : children) {
            child.setIsVisible(0); // 👑 设置为禁用
            menuMapper.updateById(child);
            cascadeHideChildren(child.getMenuId());
        }
    }

    private SysMenu requireMenu(Long menuId) {
        SysMenu menu = menuMapper.selectById(menuId);
        if (menu == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, 2013, "菜单不存在");
        }
        return menu;
    }

  private void validateParent(Long parentId, Long selfMenuId) {
    if (parentId == null || parentId == 0L) {
      return;
    }
    if (selfMenuId != null && selfMenuId.equals(parentId)) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 2029, "父菜单不能是自身");
    }
    if (menuMapper.selectById(parentId) == null) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 2030, "父菜单不存在");
    }
  }

  private void assertNotMoveToDescendant(Long menuId, Long newParentId) {
    if (newParentId == null || newParentId == 0L) {
      return;
    }
    List<SysMenu> menus = menuMapper.selectList(null);
    Map<Long, List<Long>> childrenMap = menus.stream().collect(Collectors.groupingBy(
      menu -> menu.getParentId() == null ? 0L : menu.getParentId(),
      Collectors.mapping(SysMenu::getMenuId, Collectors.toList())));
    Set<Long> descendantIds = new HashSet<>();
    collectDescendantIds(menuId, childrenMap, descendantIds);
    if (descendantIds.contains(newParentId)) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 2031, "不能将菜单移动到其子菜单下");
    }
  }

  private void validateVisibleParent(Long parentId, Integer isVisible) {
    if (isVisible == null || isVisible != 1 || parentId == null || parentId == 0L) {
      return;
    }
    SysMenu parent = menuMapper.selectById(parentId);
    if (parent != null && parent.getIsVisible() == 0) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 2011, "上级菜单已禁用，无法将子项设为启用");
    }
  }

  private void collectDescendantIds(Long menuId, Map<Long, List<Long>> childrenMap, Set<Long> ids) {
    if (!ids.add(menuId)) {
      return;
    }
    for (Long childId : childrenMap.getOrDefault(menuId, List.of())) {
      collectDescendantIds(childId, childrenMap, ids);
    }
  }

    private List<MenuTreeVO> buildTree(List<SysMenu> menus) {
        Map<Long, MenuTreeVO> nodeMap = menus.stream()
            .collect(Collectors.toMap(SysMenu::getMenuId, menuConverter::toTreeNode));
        List<MenuTreeVO> roots = new ArrayList<>();
        for (SysMenu menu : menus) {
            MenuTreeVO node = nodeMap.get(menu.getMenuId());
            if (menu.getParentId() == null || menu.getParentId() == 0L) {
                roots.add(node);
            } else {
                MenuTreeVO parent = nodeMap.get(menu.getParentId());
                if (parent != null) {
                    parent.getChildren().add(node);
                } else {
                    roots.add(node);
                }
            }
        }
        return roots;
    }
}

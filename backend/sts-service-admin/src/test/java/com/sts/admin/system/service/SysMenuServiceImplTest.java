package com.sts.admin.system.service;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import com.sts.admin.entity.SysMenu;
import com.sts.admin.mapper.SysMenuMapper;
import com.sts.admin.mapper.SysRoleMenuMapper;
import com.sts.admin.tenant.TenantSessionKeys;
import com.sts.admin.system.converter.SysMenuConverter;
import com.sts.admin.system.dto.MenuBatchVisibilityRequest;
import com.sts.admin.system.dto.MenuBatchVisibilityRequest.MenuVersionItem;
import com.sts.admin.system.dto.MenuCreateRequest;
import com.sts.admin.system.dto.MenuUpdateRequest;
import com.sts.admin.system.vo.MenuTreeVO;
import com.sts.common.exception.BusinessException;
import com.sts.org.service.TenantDirectory;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * {@link SysMenuServiceImpl} 业务逻辑单元测试。
 * <p>
 * 仅 2 个依赖（menuMapper / menuConverter），纯 Mockito。覆盖：
 * 树装配、可见性父子约束、禁用级联、批量可见性、以及 {@code getSidebarMenuTree} 的
 * superAdmin / 普通用户两条分支（{@code StpUtil} 静态调用用 mockStatic 隔离）。
 */
class SysMenuServiceImplTest {

    private SysMenuMapper menuMapper;
  private SysRoleMenuMapper roleMenuMapper;
    private SysMenuConverter menuConverter;
    private SysMenuServiceImpl service;

    @BeforeEach
    void setUp() {
        menuMapper = mock(SysMenuMapper.class);
      roleMenuMapper = mock(SysRoleMenuMapper.class);
        menuConverter = mock(SysMenuConverter.class);
      service = new SysMenuServiceImpl(menuMapper, roleMenuMapper, menuConverter);
    }

    // ---------- 树装配 ----------

    @Test
    @DisplayName("getMenuTree：扁平列表按 parentId 装配成树")
    void getMenuTree_buildsNestedTree() {
        // 1(根) - {2,3 子}; 4(根)
        when(menuMapper.selectList(any())).thenReturn(List.of(
            menu(1L, 0L), menu(2L, 1L), menu(3L, 1L), menu(4L, 0L)));
        stubToTreeNode();

        List<MenuTreeVO> tree = service.getMenuTree(null, null, null);

        assertThat(tree).extracting(MenuTreeVO::getMenuId).containsExactly(1L, 4L);
        assertThat(tree.get(0).getChildren()).extracting(MenuTreeVO::getMenuId).containsExactly(2L, 3L);
        assertThat(tree.get(1).getChildren()).isEmpty();
    }

  // ---------- CRUD ----------

  @Test
  @DisplayName("createMenu：父菜单存在 → 转换、插入并返回菜单 ID")
  void createMenu_withParent_insertsAndReturnsId() {
    when(menuMapper.selectById(1L)).thenReturn(menu(1L, 0L));
    SysMenu entity = menu(null, 1L);
    when(menuConverter.toEntity(any(MenuCreateRequest.class))).thenReturn(entity);
    when(menuMapper.insert(entity)).thenAnswer(invocation -> {
      entity.setMenuId(10L);
      return 1;
    });

    Long id = service.createMenu(createRequest(1L));

    assertThat(id).isEqualTo(10L);
    verify(menuMapper).insert(entity);
  }

  @Test
  @DisplayName("updateMenu：父菜单指向子菜单 → 400/2031，且不落库")
  void updateMenu_moveToDescendant_badRequest() {
    when(menuMapper.selectById(1L)).thenReturn(menu(1L, 0L));
    when(menuMapper.selectById(2L)).thenReturn(menu(2L, 1L));
    when(menuMapper.selectList(null)).thenReturn(List.of(menu(1L, 0L), menu(2L, 1L)));

    assertBusinessError(() -> service.updateMenu(1L, updateRequest(2L)), HttpStatus.BAD_REQUEST, 2031);

    verify(menuMapper, never()).updateByIdWithVersionCheck(any());
  }

  @Test
  @DisplayName("deleteMenu：存在未删除角色关联 → 409/2028，且不解绑、不删除")
  void deleteMenu_roleBound_conflict() {
    when(menuMapper.selectById(1L)).thenReturn(menu(1L, 0L));
    when(menuMapper.selectCount(any())).thenReturn(0L);
    when(roleMenuMapper.countLiveRoleBindingsByMenuId(1L)).thenReturn(1L);

    assertBusinessError(() -> service.deleteMenu(1L), HttpStatus.CONFLICT, 2028);

    verify(roleMenuMapper).countLiveRoleBindingsByMenuId(1L);
    verify(roleMenuMapper, never()).delete(any());
    verify(menuMapper, never()).update(any(), any());
  }

    // ---------- updateVisibility ----------

    @Test
    @DisplayName("updateVisibility：菜单不存在 → 404/2013")
    void updateVisibility_notFound_notFound() {
        when(menuMapper.selectById(5L)).thenReturn(null);

        assertBusinessError(() -> service.updateVisibility(5L, 0, 1), HttpStatus.NOT_FOUND, 2013);
    }

    @Test
    @DisplayName("updateVisibility：上级不可见时启用子项 → 400/2011，不落库")
    void updateVisibility_enableWhileParentHidden_badRequest() {
        when(menuMapper.selectById(5L)).thenReturn(menu(5L, 1L));
        SysMenu parent = menu(1L, 0L);
        parent.setIsVisible(0);
        when(menuMapper.selectById(1L)).thenReturn(parent);

        assertBusinessError(() -> service.updateVisibility(5L, 1, 0), HttpStatus.BAD_REQUEST, 2011);

        verify(menuMapper, never()).updateByIdWithVersionCheck(any());
    }

    @Test
    @DisplayName("updateVisibility：上级可见时启用子项 → 落库，且不触发级联")
    void updateVisibility_enableWithVisibleParent_updates() {
        when(menuMapper.selectById(5L)).thenReturn(menu(5L, 1L));
        when(menuMapper.selectById(1L)).thenReturn(menu(1L, 0L)); // parent isVisible=1

        service.updateVisibility(5L, 1, 0);

        verify(menuMapper).updateByIdWithVersionCheck(any(SysMenu.class));
        verify(menuMapper, never()).selectList(any()); // 启用不级联
    }

    @Test
    @DisplayName("updateVisibility：禁用节点 → 落库并级联禁用可见子节点")
    void updateVisibility_disable_cascadesToChildren() {
        when(menuMapper.selectById(5L)).thenReturn(menu(5L, 0L));
        // 级联第一层返回子 10，第二层无子
        when(menuMapper.selectList(any())).thenReturn(List.of(menu(10L, 5L)), List.of());

        service.updateVisibility(5L, 0, 0);

        verify(menuMapper).updateByIdWithVersionCheck(any(SysMenu.class));
        ArgumentCaptor<SysMenu> childCaptor = ArgumentCaptor.forClass(SysMenu.class);
        verify(menuMapper).updateById(childCaptor.capture());
        assertThat(childCaptor.getValue().getMenuId()).isEqualTo(10L);
        assertThat(childCaptor.getValue().getIsVisible()).isEqualTo(0); // 子被级联禁用
    }

    // ---------- batchUpdateVisibility ----------

    @Test
    @DisplayName("batchUpdateVisibility：items 为空 → 直接返回，不落库")
    void batchUpdateVisibility_emptyItems_noop() {
        service.batchUpdateVisibility(List.of(), List.of());

        verify(menuMapper, never()).updateByIdWithVersionCheck(any());
    }

    @Test
    @DisplayName("batchUpdateVisibility：按可见集合逐项设置 isVisible")
    void batchUpdateVisibility_setsVisibilityPerSet() {
        service.batchUpdateVisibility(List.of(10L), List.of(item(10L, 1), item(11L, 1)));

        ArgumentCaptor<SysMenu> captor = ArgumentCaptor.forClass(SysMenu.class);
        verify(menuMapper, times(2)).updateByIdWithVersionCheck(captor.capture());
        SysMenu m10 = captor.getAllValues().stream().filter(m -> m.getMenuId() == 10L).findFirst().orElseThrow();
        SysMenu m11 = captor.getAllValues().stream().filter(m -> m.getMenuId() == 11L).findFirst().orElseThrow();
        assertThat(m10.getIsVisible()).isEqualTo(1); // 在可见集合内
        assertThat(m11.getIsVisible()).isEqualTo(0); // 不在集合内
    }

    // ---------- getSidebarMenuTree ----------

    @Test
    @DisplayName("getSidebarMenuTree：superAdmin 拉取全部菜单")
    void getSidebarMenuTree_superAdmin_loadsAll() {
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(1L);
            stp.when(() -> StpUtil.hasPermission("*")).thenReturn(true);
            stp.when(StpUtil::getTokenSession)
                .thenReturn(sessionWithOrgType(TenantDirectory.TYPE_ENTERPRISE));
            when(menuMapper.selectList(any())).thenReturn(List.of(menu(1L, 0L), menu(2L, 1L)));
            stubToTreeNode();

            List<MenuTreeVO> tree = service.getSidebarMenuTree();

            assertThat(tree).hasSize(1);
            assertThat(tree.get(0).getChildren()).hasSize(1);
        }
        verify(menuMapper).selectList(any());
        verify(menuMapper, never()).selectAccessibleMenusByUserId(any());
    }

    @Test
    @DisplayName("getSidebarMenuTree：superAdmin 企业会话剔除园区监管模块")
    void getSidebarMenuTree_superAdmin_enterpriseSession_hidesRegulationModule() {
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(1L);
            stp.when(() -> StpUtil.hasPermission("*")).thenReturn(true);
            stp.when(StpUtil::getTokenSession)
                .thenReturn(sessionWithOrgType(TenantDirectory.TYPE_ENTERPRISE));
            when(menuMapper.selectList(any())).thenReturn(List.of(
                menu(9800L, 0L, 9800L),   // 企业工作台模块
                menu(9801L, 9800L, 9800L),
                menu(9810L, 0L, 9810L),   // 园区监管模块
                menu(9811L, 9810L, 9810L),
                menu(9000L, 0L, null)));  // 无归属模块的菜单必须保留
            stubToTreeNode();

            List<MenuTreeVO> tree = service.getSidebarMenuTree();

            assertThat(tree).extracting(MenuTreeVO::getMenuId).containsExactly(9800L, 9000L);
            assertThat(tree.get(0).getChildren()).extracting(MenuTreeVO::getMenuId).containsExactly(9801L);
        }
    }

    @Test
    @DisplayName("getSidebarMenuTree：superAdmin 监管会话剔除企业工作台模块")
    void getSidebarMenuTree_superAdmin_regulatorSession_hidesEnterpriseWorkbench() {
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(1L);
            stp.when(() -> StpUtil.hasPermission("*")).thenReturn(true);
            stp.when(StpUtil::getTokenSession)
                .thenReturn(sessionWithOrgType(TenantDirectory.TYPE_REGULATOR));
            when(menuMapper.selectList(any())).thenReturn(List.of(
                menu(9800L, 0L, 9800L),
                menu(9810L, 0L, 9810L),
                menu(9811L, 9810L, 9810L)));
            stubToTreeNode();

            List<MenuTreeVO> tree = service.getSidebarMenuTree();

            assertThat(tree).extracting(MenuTreeVO::getMenuId).containsExactly(9810L);
            assertThat(tree.get(0).getChildren()).extracting(MenuTreeVO::getMenuId).containsExactly(9811L);
        }
    }

    @Test
    @DisplayName("hiddenModuleIds：两类专属模块按会话租户类型互斥；培训机构同属非监管")
    void hiddenModuleIds_switchesExclusiveModulesByTenantType() {
        assertThat(SysMenuServiceImpl.hiddenModuleIds(TenantDirectory.TYPE_ENTERPRISE))
            .containsExactly(9810L);
        assertThat(SysMenuServiceImpl.hiddenModuleIds(TenantDirectory.TYPE_TRAINING_INSTITUTION))
            .containsExactly(9810L);
        assertThat(SysMenuServiceImpl.hiddenModuleIds(TenantDirectory.TYPE_REGULATOR))
            .containsExactly(9800L);
    }

    @Test
    @DisplayName("getSidebarMenuTree：普通用户按权限拉取可访问菜单")
    void getSidebarMenuTree_normalUser_loadsAccessible() {
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(2L);
            stp.when(() -> StpUtil.hasPermission("*")).thenReturn(false);
            when(menuMapper.selectAccessibleMenusByUserId(2L)).thenReturn(List.of(menu(1L, 0L)));
            stubToTreeNode();

            List<MenuTreeVO> tree = service.getSidebarMenuTree();

            assertThat(tree).hasSize(1);
        }
        verify(menuMapper).selectAccessibleMenusByUserId(2L);
        verify(menuMapper, never()).selectList(any());
    }

    // ---------- helpers ----------

    private void stubToTreeNode() {
        when(menuConverter.toTreeNode(any())).thenAnswer(inv -> {
            SysMenu m = inv.getArgument(0);
            MenuTreeVO vo = new MenuTreeVO();
            vo.setMenuId(m.getMenuId());
            vo.setParentId(m.getParentId());
            return vo;
        });
    }

    private static SysMenu menu(Long menuId, Long parentId) {
        SysMenu m = new SysMenu();
        m.setMenuId(menuId);
        m.setParentId(parentId);
        m.setIsVisible(1);
        return m;
    }

    private static SysMenu menu(Long menuId, Long parentId, Long moduleId) {
        SysMenu m = menu(menuId, parentId);
        m.setModuleId(moduleId);
        return m;
    }

    /**
     * {@code SaSession.getString} 是 final 方法，无法 stub，这里用真实 Session 写值。
     */
    private static SaSession sessionWithOrgType(String orgType) {
        SaSession session = new SaSession("test-token");
        if (orgType != null) {
            session.set(TenantSessionKeys.ORG_TYPE, orgType);
        }
        return session;
    }

    private static MenuVersionItem item(Long menuId, Integer version) {
        MenuVersionItem it = new MenuBatchVisibilityRequest.MenuVersionItem();
        it.setMenuId(menuId);
        it.setVersion(version);
        return it;
    }

  private static MenuCreateRequest createRequest(Long parentId) {
    MenuCreateRequest request = new MenuCreateRequest();
    request.setParentId(parentId);
    request.setMenuName("菜单");
    request.setMenuType("M");
    request.setIsVisible(1);
    return request;
  }

  private static MenuUpdateRequest updateRequest(Long parentId) {
    MenuUpdateRequest request = new MenuUpdateRequest();
    request.setParentId(parentId);
    request.setMenuName("菜单");
    request.setMenuType("M");
    request.setIsVisible(1);
    request.setVersion(1);
    return request;
  }

    private static void assertBusinessError(ThrowingCallable call, HttpStatus status, int code) {
        assertThatThrownBy(call)
            .isInstanceOfSatisfying(BusinessException.class, ex -> {
                assertThat(ex.getStatus()).isEqualTo(status);
                assertThat(ex.getCode()).isEqualTo(code);
            });
    }
}

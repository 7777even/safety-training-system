package com.sts.admin.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sts.admin.entity.SysMenu;
import com.sts.admin.entity.SysRole;
import com.sts.admin.entity.SysRoleMenu;
import com.sts.admin.mapper.SysMenuMapper;
import com.sts.admin.mapper.SysRoleMapper;
import com.sts.admin.mapper.SysRoleMenuMapper;
import com.sts.admin.support.AbstractMysqlIntegrationTest;
import com.sts.admin.support.MapperItConfig;
import com.sts.admin.system.converter.SysMenuConverter;
import com.sts.admin.system.dto.MenuCreateRequest;
import com.sts.admin.system.dto.MenuUpdateRequest;
import com.sts.admin.system.service.SysMenuService;
import com.sts.admin.system.service.SysMenuServiceImpl;
import com.sts.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 菜单 CRUD 真 MySQL 集成测试：验证自增主键、乐观锁更新、逻辑删除以及角色关联删除护栏。
 */
@SpringBootTest(classes = {MapperItConfig.class, MenuCrudPersistenceIT.ServiceConfig.class},
  webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class MenuCrudPersistenceIT extends AbstractMysqlIntegrationTest {

  @Autowired
  private SysMenuService menuService;
  @Autowired
  private SysMenuMapper menuMapper;
  @Autowired
  private SysRoleMapper roleMapper;
  @Autowired
  private SysRoleMenuMapper roleMenuMapper;
  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Test
  @DisplayName("菜单 CRUD：真库新增、乐观锁修改并逻辑删除")
  void createUpdateAndDelete_persistsWithOptimisticLockAndLogicDelete() {
    Long menuId = menuService.createMenu(createRequest("安全管理"));
    SysMenu created = menuMapper.selectById(menuId);
    assertThat(created).isNotNull();
    assertThat(created.getMenuName()).isEqualTo("安全管理");
    Integer originalVersion = created.getVersion();

    MenuUpdateRequest update = updateRequest("安全设置", originalVersion);
    menuService.updateMenu(menuId, update);
    SysMenu updated = menuMapper.selectById(menuId);
    assertThat(updated.getMenuName()).isEqualTo("安全设置");
    assertThat(updated.getVersion()).isEqualTo(originalVersion + 1);

    menuService.deleteMenu(menuId);
    assertThat(menuMapper.selectById(menuId)).isNull();
  }

  @Test
  @DisplayName("删除菜单：真库存在未删除角色关联时返回 409/2028 且保留菜单")
  void deleteMenu_liveRoleBindingExists_conflictAndKeepsMenu() {
    Long menuId = menuService.createMenu(createRequest("角色菜单"));
    SysRole role = insertRole("menu-bind-live");
    insertRoleMenu(role.getRoleId(), menuId);

    assertThatThrownBy(() -> menuService.deleteMenu(menuId))
      .isInstanceOfSatisfying(BusinessException.class, ex -> {
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ex.getCode()).isEqualTo(2028);
      });
    assertThat(menuMapper.selectById(menuId)).isNotNull();
    assertThat(roleMenuMapper.selectCount(new LambdaQueryWrapper<SysRoleMenu>()
                                            .eq(SysRoleMenu::getMenuId, menuId))).isEqualTo(1L);
  }

  @Test
  @DisplayName("删除菜单：role_menu 指向不存在的角色时视为无关联，允许删除并清理孤儿")
  void deleteMenu_orphanRoleMenu_allowsDeleteAndCleansBinding() {
    Long menuId = menuService.createMenu(createRequest("孤儿关联菜单"));
    insertRoleMenu(99001L, menuId);

    menuService.deleteMenu(menuId);

    assertThat(menuMapper.selectById(menuId)).isNull();
    assertThat(roleMenuMapper.selectCount(new LambdaQueryWrapper<SysRoleMenu>()
                                            .eq(SysRoleMenu::getMenuId, menuId))).isZero();
  }

  @Test
  @DisplayName("删除菜单：角色已软删但残留 role_menu 时允许删除并清理孤儿")
  void deleteMenu_softDeletedRoleBinding_allowsDeleteAndCleansBinding() {
    Long menuId = menuService.createMenu(createRequest("软删角色菜单"));
    SysRole role = insertRole("menu-bind-deleted");
    insertRoleMenu(role.getRoleId(), menuId);
    jdbcTemplate.update("UPDATE us_sys_role SET deleted = ? WHERE role_id = ?",
                        role.getRoleId(), role.getRoleId());

    menuService.deleteMenu(menuId);

    assertThat(menuMapper.selectById(menuId)).isNull();
    assertThat(roleMenuMapper.selectCount(new LambdaQueryWrapper<SysRoleMenu>()
                                            .eq(SysRoleMenu::getMenuId, menuId))).isZero();
  }

  private SysRole insertRole(String roleCode) {
    SysRole role = new SysRole();
    role.setRoleCode(roleCode);
    role.setRoleName(roleCode);
    role.setRoleLevel("ADMIN");
    role.setIsBuiltin(0);
    role.setDescription("");
    role.setStatus(1);
    roleMapper.insert(role);
    return role;
  }

  private void insertRoleMenu(Long roleId, Long menuId) {
    SysRoleMenu binding = new SysRoleMenu();
    binding.setRoleId(roleId);
    binding.setMenuId(menuId);
    roleMenuMapper.insert(binding);
  }

  private static MenuCreateRequest createRequest(String name) {
    MenuCreateRequest request = new MenuCreateRequest();
    request.setParentId(0L);
    request.setMenuName(name);
    request.setMenuType("M");
    request.setIsVisible(1);
    request.setSortOrder(0);
    return request;
  }

  private static MenuUpdateRequest updateRequest(String name, Integer version) {
    MenuUpdateRequest request = new MenuUpdateRequest();
    request.setParentId(0L);
    request.setMenuName(name);
    request.setMenuType("M");
    request.setIsVisible(1);
    request.setSortOrder(0);
    request.setVersion(version);
    return request;
  }

  @TestConfiguration
  static class ServiceConfig {
    @Bean
    SysMenuConverter sysMenuConverter() {
      return new SysMenuConverter() {
        @Override
        public com.sts.admin.system.vo.MenuTreeVO toTreeNode(SysMenu menu) {
          return new com.sts.admin.system.vo.MenuTreeVO();
        }

        @Override
        public SysMenu toEntity(MenuCreateRequest request) {
          SysMenu menu = new SysMenu();
          menu.setParentId(request.getParentId());
          menu.setMenuName(request.getMenuName());
          menu.setPath(request.getPath());
          menu.setComponent(request.getComponent());
          menu.setPerms(request.getPerms());
          menu.setIcon(request.getIcon());
          menu.setSortOrder(request.getSortOrder());
          menu.setIsVisible(request.getIsVisible());
          menu.setMenuType(request.getMenuType());
          menu.setModuleId(request.getModuleId());
          return menu;
        }

        @Override
        public void updateEntity(SysMenu menu, MenuUpdateRequest request) {
          menu.setParentId(request.getParentId());
          menu.setMenuName(request.getMenuName());
          menu.setPath(request.getPath());
          menu.setComponent(request.getComponent());
          menu.setPerms(request.getPerms());
          menu.setIcon(request.getIcon());
          menu.setSortOrder(request.getSortOrder());
          menu.setIsVisible(request.getIsVisible());
          menu.setMenuType(request.getMenuType());
          menu.setModuleId(request.getModuleId());
        }
      };
    }

    @Bean
    SysMenuService sysMenuService(SysMenuMapper menuMapper, SysRoleMenuMapper roleMenuMapper,
                                  SysMenuConverter menuConverter) {
      return new SysMenuServiceImpl(menuMapper, roleMenuMapper, menuConverter);
    }
  }
}

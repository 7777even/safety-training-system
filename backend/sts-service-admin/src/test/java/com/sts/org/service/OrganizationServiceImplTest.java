package com.sts.org.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sts.admin.cache.CacheEvictService;
import com.sts.admin.entity.*;
import com.sts.admin.mapper.*;
import com.sts.admin.tenant.TenantContext;
import com.sts.common.api.PageRequest;
import com.sts.common.exception.BusinessException;
import com.sts.org.dto.OrganizationCreateRequest;
import com.sts.org.dto.OrganizationQuery;
import com.sts.org.dto.OrganizationUpdateRequest;
import com.sts.org.entity.Organization;
import com.sts.org.mapper.OrganizationMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 租户主数据护栏单测（零 DB）。
 * <p>
 * 覆盖两类租户、监管平台唯一性、编码唯一与格式、superAdmin 写权限边界。
 */
class OrganizationServiceImplTest {

  private static final long SUPER_ADMIN_ID = 1L;
  private static final long NORMAL_USER_ID = 7L;

  private OrganizationMapper organizationMapper;
  private SysUserMapper userMapper;
  private SysRoleMapper roleMapper;
  private SysUserRoleMapper userRoleMapper;
  private SysAuthIdentityMapper authIdentityMapper;
  private SysRoleMenuMapper roleMenuMapper;
  private SysMenuMapper menuMapper;
  private PasswordEncoder passwordEncoder;
  private CacheEvictService cacheEvictService;
  private OrganizationServiceImpl service;

  @BeforeEach
  void setUp() {
    organizationMapper = mock(OrganizationMapper.class);
    userMapper = mock(SysUserMapper.class);
    roleMapper = mock(SysRoleMapper.class);
    userRoleMapper = mock(SysUserRoleMapper.class);
    authIdentityMapper = mock(SysAuthIdentityMapper.class);
    roleMenuMapper = mock(SysRoleMenuMapper.class);
    menuMapper = mock(SysMenuMapper.class);
    passwordEncoder = new BCryptPasswordEncoder(10);
    cacheEvictService = mock(CacheEvictService.class);
    service = new OrganizationServiceImpl(organizationMapper,
                                          userMapper,
                                          roleMapper,
                                          userRoleMapper,
                                          roleMenuMapper,
                                          menuMapper,
                                          authIdentityMapper,
                                          passwordEncoder,
                                          cacheEvictService);
    givenSuperAdmin();
    givenNormalUser();
  }

  @AfterEach
  void clearTenantContext() {
    TenantContext.clear();
  }

  private void givenSuperAdmin() {
    SysUser user = new SysUser();
    user.setUserCode("superAdmin");
    user.setStatus(1);
    when(userMapper.selectById(SUPER_ADMIN_ID)).thenReturn(user);
  }

  private void givenNormalUser() {
    SysUser user = new SysUser();
    user.setUserCode("zhangsan");
    user.setStatus(1);
    when(userMapper.selectById(NORMAL_USER_ID)).thenReturn(user);
  }

  private static OrganizationCreateRequest request(String orgCode, String orgName, String orgType) {
    OrganizationCreateRequest request = new OrganizationCreateRequest();
    request.setOrgCode(orgCode);
    request.setOrgName(orgName);
    request.setOrgType(orgType);
    request.setAdminPassword("deadbeef");
    return request;
  }

  private void stubInsertReturningId(long organizationId) {
    when(organizationMapper.insert(any(Organization.class))).thenAnswer(invocation -> {
      ((Organization) invocation.getArgument(0)).setOrganizationId(organizationId);
      return 1;
    });
  }

  @Test
  void createOrganization_regulatorCreatesTrainingInstitution() {
    givenRegulatorUser();
    stubInsertReturningId(33L);
    when(roleMapper.insert(any(SysRole.class))).thenAnswer(invocation -> {
      ((SysRole) invocation.getArgument(0)).setRoleId(34L);
      return 1;
    });
    when(userMapper.insert(any(SysUser.class))).thenAnswer(invocation -> {
      ((SysUser) invocation.getArgument(0)).setUserId(44L);
      return 1;
    });
    when(menuMapper.selectList(any())).thenReturn(java.util.List.of());

    assertThat(service.createOrganization(80L, request("TRAIN-02", "园区培训机构", "TRAINING_INSTITUTION")))
      .isEqualTo(33L);
  }

  @Test
  void createOrganization_regulatorCannotCreateRegulatorTenant() {
    givenRegulatorUser();
    assertThatThrownBy(() -> service.createOrganization(80L, request("GOV-X", "另一个监管", "REGULATOR")))
      .isInstanceOfSatisfying(BusinessException.class, ex -> assertThat(ex.getCode()).isEqualTo(4048));
    verify(organizationMapper, never()).insert(any(Organization.class));
  }

  @Test
  void updateOrganization_allowsEnterpriseToTrainingInstitution() {
    Organization existing = new Organization();
    existing.setOrganizationId(21L);
    existing.setOrgCode("ACME");
    existing.setOrgType("ENTERPRISE");
    when(organizationMapper.selectById(21L)).thenReturn(existing);

    OrganizationUpdateRequest request = new OrganizationUpdateRequest();
    request.setOrgName("示范培训机构");
    request.setOrgType("TRAINING_INSTITUTION");
    request.setStatus(1);
    request.setVersion(3);
    service.updateOrganization(SUPER_ADMIN_ID, 21L, request);

    verify(organizationMapper).updateByIdWithVersionCheck(argThat((Organization org) ->
                                                                    "TRAINING_INSTITUTION".equals(org.getOrgType())));
  }

  private void givenRegulatorUser() {
    SysUser user = new SysUser();
    user.setUserId(80L);
    user.setUserCode("regulator-admin");
    user.setStatus(1);
    user.setOrganizationId(9L);
    when(userMapper.selectById(80L)).thenReturn(user);
    Organization regulator = new Organization();
    regulator.setOrganizationId(9L);
    regulator.setOrgType("REGULATOR");
    regulator.setStatus(1);
    when(organizationMapper.selectById(9L)).thenReturn(regulator);
  }

  @Test
  void createOrganization_superAdminCreatesEnterpriseTenant() {
    stubInsertReturningId(21L);
    when(roleMapper.insert(any(SysRole.class))).thenAnswer(invocation -> {
      assertThat(TenantContext.requireTenantId()).isEqualTo(21L);
      ((SysRole) invocation.getArgument(0)).setRoleId(31L);
      return 1;
    });
    when(userMapper.insert(any(SysUser.class))).thenAnswer(invocation -> {
      assertThat(TenantContext.requireTenantId()).isEqualTo(21L);
      ((SysUser) invocation.getArgument(0)).setUserId(41L);
      return 1;
    });
    SysMenu firstMenu = new SysMenu();
    firstMenu.setMenuId(51L);
    SysMenu secondMenu = new SysMenu();
    secondMenu.setMenuId(52L);
    SysMenu organizationMenu = new SysMenu();
    organizationMenu.setMenuId(9532L);
    SysMenu tenantAddMenu = new SysMenu();
    tenantAddMenu.setMenuId(1382L);
    SysMenu tenantQueryMenu = new SysMenu();
    tenantQueryMenu.setMenuId(1381L);
    SysMenu tenantEditMenu = new SysMenu();
    tenantEditMenu.setMenuId(1383L);
    SysMenu correctionMenu = new SysMenu(); correctionMenu.setMenuId(9722L); correctionMenu.setPerms("archive:history:correct");
    SysMenu correctionListMenu = new SysMenu(); correctionListMenu.setMenuId(9723L); correctionListMenu.setPerms("archive:history:correction:list");
    when(menuMapper.selectList(any())).thenReturn(java.util.List.of(
      firstMenu, organizationMenu, tenantQueryMenu, tenantAddMenu, tenantEditMenu, secondMenu, correctionMenu, correctionListMenu));
    TenantContext.setTenantId(5L);

    assertThat(service.createOrganization(SUPER_ADMIN_ID, request(" acme-01 ", " 示范企业 ", "ENTERPRISE")))
      .isEqualTo(21L);

    verify(organizationMapper).insert(argThat((Organization org) ->
                                                "ACME-01".equals(org.getOrgCode())
                                                  && "示范企业".equals(org.getOrgName())
                                                  && "ENTERPRISE".equals(org.getOrgType())
                                                  && Integer.valueOf(1).equals(org.getStatus())));
    verify(userMapper).insert(argThat((SysUser user) ->
                                        "admin".equals(user.getUserCode())
                                          && user.getDeptId() == null));
    verify(authIdentityMapper).insert(argThat((SysAuthIdentity identity) ->
                                                Long.valueOf(41L).equals(identity.getUserId())
                                                  && "ACME-01:admin".equals(identity.getIdentifier())));
    verify(userRoleMapper).insert(argThat((SysUserRole userRole) ->
                                            Long.valueOf(41L).equals(userRole.getUserId())
                                              && Long.valueOf(31L).equals(userRole.getRoleId())));
    ArgumentCaptor<SysRoleMenu> grantedMenus = ArgumentCaptor.forClass(SysRoleMenu.class);
    verify(roleMenuMapper, times(2)).insert(grantedMenus.capture());
    assertThat(grantedMenus.getAllValues()).extracting(SysRoleMenu::getMenuId).containsExactly(51L, 52L);
    assertThat(TenantContext.requireTenantId()).isEqualTo(5L);
  }

  @Test
  void pageOrganizations_checksPlatformIdentityOutsideSelectedTenant() {
    TenantContext.setTenantId(88L);
    when(userMapper.selectById(SUPER_ADMIN_ID)).thenAnswer(invocation -> {
      assertThat(TenantContext.isBypass()).isTrue();
      SysUser user = new SysUser();
      user.setUserCode("superAdmin");
      user.setStatus(1);
      return user;
    });
    when(organizationMapper.selectPage(any(), any())).thenReturn(new Page<>());

    PageRequest<OrganizationQuery> request = new PageRequest<>();
    request.setQuery(new OrganizationQuery());
    service.pageOrganizations(SUPER_ADMIN_ID, request);

    assertThat(TenantContext.requireTenantId()).isEqualTo(88L);
  }

  @Test
  void createOrganization_rejectsNonSuperAdmin() {
    assertThatThrownBy(() -> service.createOrganization(NORMAL_USER_ID, request("ACME", "企业", "ENTERPRISE")))
      .isInstanceOfSatisfying(BusinessException.class, ex -> {
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(ex.getCode()).isEqualTo(4048);
      });
    verify(organizationMapper, never()).insert(any(Organization.class));
  }

  @Test
  void createOrganization_rejectsRemovedHierarchyTypes() {
    for (String legacyType : new String[]{"PLATFORM", "PARK"}) {
      assertThatThrownBy(() -> service.createOrganization(SUPER_ADMIN_ID, request("ORG", "机构", legacyType)))
        .describedAs("旧机构层级类型 %s 必须被拒绝", legacyType)
        .isInstanceOfSatisfying(BusinessException.class, ex -> {
          assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
          assertThat(ex.getCode()).isEqualTo(4042);
        });
    }
    verify(organizationMapper, never()).insert(any(Organization.class));
  }

  @Test
  void createOrganization_superAdminCreatesTrainingInstitution() {
    stubInsertReturningId(22L);
    when(roleMapper.insert(any(SysRole.class))).thenAnswer(invocation -> {
      ((SysRole) invocation.getArgument(0)).setRoleId(32L);
      return 1;
    });
    when(userMapper.insert(any(SysUser.class))).thenAnswer(invocation -> {
      ((SysUser) invocation.getArgument(0)).setUserId(42L);
      return 1;
    });
    SysMenu allowedMenu = new SysMenu();
    allowedMenu.setMenuId(201L);
    SysMenu organizationMenu = new SysMenu();
    organizationMenu.setMenuId(9532L);
    when(menuMapper.selectList(any())).thenReturn(java.util.List.of(allowedMenu, organizationMenu));

    assertThat(service.createOrganization(SUPER_ADMIN_ID, request("TRAIN-01", "示范培训机构", "TRAINING_INSTITUTION")))
      .isEqualTo(22L);
    verify(organizationMapper).insert(argThat((Organization org) ->
                                                "TRAINING_INSTITUTION".equals(org.getOrgType())));
    verify(roleMenuMapper).insert(argThat((SysRoleMenu roleMenu) ->
                                           Long.valueOf(201L).equals(roleMenu.getMenuId())));
  }

  @Test
  void createOrganization_rejectsRegulatorTenantEvenForSuperAdmin() {
    assertThatThrownBy(() -> service.createOrganization(SUPER_ADMIN_ID, request("GOV", "监管平台", "REGULATOR")))
      .isInstanceOfSatisfying(BusinessException.class, ex -> {
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(ex.getCode()).isEqualTo(4048);
      });
    verify(organizationMapper, never()).insert(any(Organization.class));
  }

  @Test
  void createOrganization_rejectsDuplicateOrgCode() {
    when(organizationMapper.selectByOrgCode("ACME")).thenReturn(new Organization());

    assertThatThrownBy(() -> service.createOrganization(SUPER_ADMIN_ID, request("ACME", "企业", "ENTERPRISE")))
      .isInstanceOfSatisfying(BusinessException.class, ex -> {
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ex.getCode()).isEqualTo(4041);
      });
    verify(organizationMapper, never()).insert(any(Organization.class));
  }

  @Test
  void createOrganization_translatesUniqueViolationToConflict() {
    when(organizationMapper.insert(any(Organization.class))).thenThrow(new DuplicateKeyException("uk_org_code_deleted"));

    assertThatThrownBy(() -> service.createOrganization(SUPER_ADMIN_ID, request("ACME", "企业", "ENTERPRISE")))
      .isInstanceOfSatisfying(BusinessException.class, ex -> assertThat(ex.getCode()).isEqualTo(4041));
  }

  @Test
  void createOrganization_rejectsInvalidOrgCodeFormat() {
    assertThatThrownBy(() -> service.createOrganization(SUPER_ADMIN_ID, request("A", "企业", "ENTERPRISE")))
      .isInstanceOfSatisfying(BusinessException.class, ex -> {
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getCode()).isEqualTo(1001);
      });
  }

  @Test
  void createOrganization_rejectsInvalidContact() {
    SysUser disabled = new SysUser();
    disabled.setStatus(0);
    when(userMapper.selectById(9L)).thenReturn(disabled);

    OrganizationCreateRequest request = request("ACME", "企业", "ENTERPRISE");
    request.setContactUserId(9L);

    assertThatThrownBy(() -> service.createOrganization(SUPER_ADMIN_ID, request))
      .isInstanceOfSatisfying(BusinessException.class, ex -> assertThat(ex.getCode()).isEqualTo(4047));
    verify(organizationMapper, never()).insert(any(Organization.class));
  }

  @Test
  void updateOrganization_superAdminDisablesTenant() {
    Organization existing = new Organization();
    existing.setOrganizationId(21L);
    existing.setOrgCode("ACME");
    existing.setOrgType("ENTERPRISE");
    when(organizationMapper.selectById(21L)).thenReturn(existing);

    OrganizationUpdateRequest request = new OrganizationUpdateRequest();
    request.setOrgName("示范企业");
    request.setStatus(0);
    request.setVersion(3);
    service.updateOrganization(SUPER_ADMIN_ID, 21L, request);

    verify(organizationMapper).updateByIdWithVersionCheck(argThat((Organization org) ->
                                                                    Integer.valueOf(0).equals(org.getStatus())
                                                                      && Integer.valueOf(3).equals(org.getVersion())
                                                                      && "ACME".equals(org.getOrgCode())
                                                                      && "ENTERPRISE".equals(org.getOrgType())));
  }

  @Test
  void updateOrganization_rejectsNonSuperAdmin() {
    OrganizationUpdateRequest request = new OrganizationUpdateRequest();
    request.setOrgName("示范企业");
    request.setStatus(0);
    request.setVersion(1);

    assertThatThrownBy(() -> service.updateOrganization(NORMAL_USER_ID, 21L, request))
      .isInstanceOfSatisfying(BusinessException.class, ex -> assertThat(ex.getCode()).isEqualTo(4048));
    verify(organizationMapper, never()).updateByIdWithVersionCheck(any(Organization.class));
  }

  @Test
  void getOrganization_rejectsMissingTenant() {
    when(organizationMapper.selectById(404L)).thenReturn(null);

    assertThatThrownBy(() -> service.getOrganization(SUPER_ADMIN_ID, 404L))
      .isInstanceOfSatisfying(BusinessException.class, ex -> {
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ex.getCode()).isEqualTo(4040);
      });
  }

  @Test
  void pageOrganizations_listsTenantsWithoutPlaceholderFiltering() {
    when(organizationMapper.selectPage(any(), any())).thenReturn(new Page<>());

    PageRequest<OrganizationQuery> request = new PageRequest<>();
    request.setQuery(new OrganizationQuery());
    service.pageOrganizations(SUPER_ADMIN_ID, request);

    verify(organizationMapper).selectPage(any(), any());
  }
}

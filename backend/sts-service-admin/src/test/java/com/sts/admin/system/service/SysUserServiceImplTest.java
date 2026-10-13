package com.sts.admin.system.service;

import cn.dev33.satoken.stp.StpUtil;
import com.sts.admin.cache.CacheEvictService;
import com.sts.admin.entity.*;
import com.sts.admin.mapper.*;
import com.sts.admin.system.converter.SysUserConverter;
import com.sts.admin.system.dto.ResetPasswordRequest;
import com.sts.admin.system.dto.UserCreateRequest;
import com.sts.admin.system.dto.UserUpdateRequest;
import com.sts.admin.system.vo.UserVO;
import com.sts.admin.tenant.CurrentTenantResolver;
import com.sts.admin.tenant.TenantContext;
import com.sts.common.exception.BusinessException;
import com.sts.org.entity.Organization;
import com.sts.org.mapper.OrgUserPositionMapper;
import com.sts.org.mapper.OrgUserTagMapper;
import com.sts.org.mapper.OrganizationMapper;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * {@link SysUserServiceImpl} 业务逻辑单元测试(写操作)。
 * <p>
 * 手动 mock 全部 12 个协作方(宽松打桩),不加载 Spring / MyBatis / DB。
 * 覆盖 superAdmin 三道护栏、用户编码唯一性、存在性校验、密码编码、角色重绑等业务规则。
 * <p>
 * {@code deleteUser} 的落库路径经 {@code BaseServiceImpl.removeById} → {@code TableInfoHelper}
 * 逻辑删除,依赖 MyBatis 表元信息注册,纯单测无法覆盖,故只测其两道前置护栏,
 * 落库行为留给 DB 集成测试。
 */
class SysUserServiceImplTest {

    private SysUserMapper userMapper;
    private SysAuthIdentityMapper authIdentityMapper;
    private SysAuthIdentityService authIdentityService;
    private SysUserRoleMapper userRoleMapper;
    private SysDeptService deptService;
    private PasswordEncoder passwordEncoder;
    private CacheEvictService cacheEvictService;
    private SysUserCacheService userCacheService;
    private SysUserConverter userConverter;
    private SysMenuMapper menuMapper;
    private SysRoleMapper roleMapper;
  private MockedStatic<StpUtil> stpUtil;
    private SysMenuService menuService;
  private SysDeptMapper deptMapper;
  private OrganizationMapper organizationMapper;
  private CurrentTenantResolver currentTenantResolver;
  private OrgUserTagMapper userTagMapper;
  private OrgUserPositionMapper userPositionMapper;

    private SysUserServiceImpl service;

    @BeforeEach
    void setUp() {
        userMapper = mock(SysUserMapper.class);
        authIdentityMapper = mock(SysAuthIdentityMapper.class);
        authIdentityService = mock(SysAuthIdentityService.class);
        userRoleMapper = mock(SysUserRoleMapper.class);
        deptService = mock(SysDeptService.class);
        passwordEncoder = mock(PasswordEncoder.class);
        cacheEvictService = mock(CacheEvictService.class);
        userCacheService = mock(SysUserCacheService.class);
        userConverter = mock(SysUserConverter.class);
        menuMapper = mock(SysMenuMapper.class);
        roleMapper = mock(SysRoleMapper.class);
        menuService = mock(SysMenuService.class);
      deptMapper = mock(SysDeptMapper.class);
      organizationMapper = mock(OrganizationMapper.class);
      currentTenantResolver = mock(CurrentTenantResolver.class);
      userTagMapper = mock(OrgUserTagMapper.class);
      userPositionMapper = mock(OrgUserPositionMapper.class);

        service = new SysUserServiceImpl(userMapper,
                                         authIdentityMapper,
                                         authIdentityService,
                                         userRoleMapper,
                                         deptService,
                                         deptMapper,
                                         passwordEncoder,
                                         cacheEvictService,
                                         userCacheService,
                                         userConverter,
                                         menuMapper,
                                         roleMapper,
                                         menuService,
                                         organizationMapper,
                                         currentTenantResolver,
                                         userTagMapper,
                                         userPositionMapper);

      stpUtil = mockStatic(StpUtil.class);
      stpUtil.when(StpUtil::getLoginIdAsLong).thenReturn(1L);
      // 当前租户及其下的部门、角色、企业编码——租户一致性校验的默认满足态
      when(currentTenantResolver.resolve(any())).thenReturn(TENANT_ID);
      SysDept dept = new SysDept();
      dept.setDeptId(10L);
      dept.setOrganizationId(TENANT_ID);
      when(deptMapper.selectById(10L)).thenReturn(dept);
      Organization organization = new Organization();
      organization.setOrganizationId(TENANT_ID);
      organization.setOrgCode("ACME");
      when(organizationMapper.selectById(TENANT_ID)).thenReturn(organization);
    }

  @AfterEach
  void tearDown() {
    stpUtil.close();
    TenantContext.clear();
  }

  private static final Long TENANT_ID = 1L;

  private static SysRole role(Long roleId) {
    SysRole role = new SysRole();
    role.setRoleId(roleId);
    role.setOrganizationId(TENANT_ID);
    return role;
    }

    // ---------- createUser ----------

    @Test
    @DisplayName("createUser：userCode=superAdmin → 403/2019,不查重不插入")
    void createUser_superAdmin_forbidden() {
        assertBusinessError(() -> service.createUser(createRequest("superAdmin")),
                            HttpStatus.FORBIDDEN, 2019);

        verify(userMapper, never()).selectCount(any());
        verify(userMapper, never()).insert(any(SysUser.class));
    }

    @Test
    @DisplayName("createUser：用户编码已存在 → 409/2001,不插入")
    void createUser_duplicateCode_conflict() {
        when(userMapper.selectCount(any())).thenReturn(1L);

        assertBusinessError(() -> service.createUser(createRequest("newuser")),
                            HttpStatus.CONFLICT, 2001);

        verify(userMapper, never()).insert(any(SysUser.class));
    }

    @Test
    @DisplayName("createUser：成功 → 插入用户、写 BCrypt 凭证、绑定角色、返回自增 id")
    void createUser_success_persistsUserIdentityAndRoles() {
        when(userMapper.selectCount(any())).thenReturn(0L);
        SysUser entity = new SysUser();
        entity.setUserCode("newuser");
        when(userConverter.toEntity(any())).thenReturn(entity);
        // 模拟 MyBatis-Plus insert 回填自增主键
        when(userMapper.insert(any(SysUser.class))).thenAnswer(inv -> {
            ((SysUser) inv.getArgument(0)).setUserId(100L);
            return 1;
        });
        when(passwordEncoder.encode("deadbeef")).thenReturn("ENC");

        UserCreateRequest req = createRequest("newuser");
        req.setRoleIds(List.of(5L, 6L));
      when(roleMapper.selectList(any())).thenReturn(List.of(role(5L), role(6L)));

        Long id = service.createUser(req);

        assertThat(id).isEqualTo(100L);
      // 凭证:type=local、identifier={企业编码}:{工号}、credential=编码后
        ArgumentCaptor<SysAuthIdentity> idc = ArgumentCaptor.forClass(SysAuthIdentity.class);
        verify(authIdentityMapper).insert(idc.capture());
        SysAuthIdentity savedIdentity = idc.getValue();
        assertThat(savedIdentity.getUserId()).isEqualTo(100L);
        assertThat(savedIdentity.getIdentityType()).isEqualTo("local");
      assertThat(savedIdentity.getIdentifier())
        .describedAs("工号租户内唯一，凭证标识须为 {企业编码}:{工号}")
        .isEqualTo("ACME:newuser");
        assertThat(savedIdentity.getCredential()).isEqualTo("ENC");
        // 两个角色各插一条中间表
        verify(userRoleMapper, times(2)).insert(any(SysUserRole.class));
    }

    // ---------- updateUser ----------

    @Test
    @DisplayName("updateUser：用户不存在 → 404/2004")
    void updateUser_notFound_notFound() {
        when(userCacheService.getById(7L)).thenReturn(null);

        assertBusinessError(() -> service.updateUser(7L, updateRequest()),
                            HttpStatus.NOT_FOUND, 2004);
    }

    @Test
    @DisplayName("updateUser：目标是 superAdmin → 403/2017,不落库")
    void updateUser_superAdmin_forbidden() {
        when(userCacheService.getById(7L)).thenReturn(user(7L, "superAdmin"));

        assertBusinessError(() -> service.updateUser(7L, updateRequest()),
                            HttpStatus.FORBIDDEN, 2017);

        verify(userMapper, never()).updateByIdWithVersionCheck(any());
    }

    @Test
    @DisplayName("updateUser：成功 → 带乐观锁落库、清旧角色重绑、失效缓存")
    void updateUser_success_updatesAndRebindsRoles() {
        SysUser existing = user(7L, "alice");
      existing.setOrganizationId(TENANT_ID);
        when(userCacheService.getById(7L)).thenReturn(existing);

        UserUpdateRequest req = updateRequest();
        req.setVersion(3);
        req.setRoleIds(List.of(5L));
      when(roleMapper.selectList(any())).thenReturn(List.of(role(5L)));

        service.updateUser(7L, req);

        assertThat(existing.getVersion()).isEqualTo(3);
        verify(userConverter).updateEntity(existing, req);
        verify(userMapper).updateByIdWithVersionCheck(existing);
        verify(userRoleMapper).delete(any());                    // 先清旧角色
        verify(userRoleMapper).insert(any(SysUserRole.class));   // 再绑新角色
        verify(userCacheService).evict(7L);
    }

    // ---------- deleteUser（仅护栏）----------

    @Test
    @DisplayName("deleteUser：用户不存在 → 404/2004")
    void deleteUser_notFound_notFound() {
        when(userCacheService.getById(9L)).thenReturn(null);

        assertBusinessError(() -> service.deleteUser(9L), HttpStatus.NOT_FOUND, 2004);
    }

    @Test
    @DisplayName("deleteUser：目标是 superAdmin → 403/2018")
    void deleteUser_superAdmin_forbidden() {
        when(userCacheService.getById(9L)).thenReturn(user(9L, "superAdmin"));

        assertBusinessError(() -> service.deleteUser(9L), HttpStatus.FORBIDDEN, 2018);

        verify(authIdentityService, never()).deleteByUserId(any());
    }

    // ---------- resetPassword ----------

    @Test
    @DisplayName("resetPassword：目标是 superAdmin → 403/2019")
    void resetPassword_superAdmin_forbidden() {
        when(userCacheService.getById(3L)).thenReturn(user(3L, "superAdmin"));

        assertBusinessError(() -> service.resetPassword(3L, resetRequest("x")),
                            HttpStatus.FORBIDDEN, 2019);

        verify(authIdentityMapper, never()).updateById(any(SysAuthIdentity.class));
    }

    @Test
    @DisplayName("resetPassword：未绑定本地凭证 → 400/2002")
    void resetPassword_noLocalIdentity_badRequest() {
        when(userCacheService.getById(3L)).thenReturn(user(3L, "bob"));
        when(authIdentityMapper.selectOne(any())).thenReturn(null);

        assertBusinessError(() -> service.resetPassword(3L, resetRequest("x")),
                            HttpStatus.BAD_REQUEST, 2002);

        verify(authIdentityMapper, never()).updateById(any(SysAuthIdentity.class));
    }

    @Test
    @DisplayName("resetPassword：成功 → 写入新 BCrypt 凭证并更新")
    void resetPassword_success_updatesCredential() {
        when(userCacheService.getById(3L)).thenReturn(user(3L, "bob"));
        SysAuthIdentity identity = new SysAuthIdentity();
        identity.setUserId(3L);
        identity.setIdentityType("local");
        when(authIdentityMapper.selectOne(any())).thenReturn(identity);
        when(passwordEncoder.encode("newhash")).thenReturn("ENC2");

        service.resetPassword(3L, resetRequest("newhash"));

        assertThat(identity.getCredential()).isEqualTo("ENC2");
        verify(authIdentityMapper).updateById(identity);
    }

  @Test
  @DisplayName("getUserProfile：superAdmin 登录其它企业仍可读取自身平台主体")
  void getUserProfile_superAdminReadsPlatformIdentityOutsideSelectedTenant() {
    SysUser superAdmin = user(1L, "superAdmin");
    superAdmin.setOrganizationId(5L);
    when(userCacheService.getById(1L)).thenAnswer(invocation -> {
      assertThat(TenantContext.isBypass()).isTrue();
      return superAdmin;
    });
    UserVO converted = new UserVO();
    when(userConverter.toVO(superAdmin)).thenReturn(converted);
    when(userRoleMapper.selectList(any())).thenReturn(List.of());
    when(userTagMapper.selectList(any())).thenReturn(List.of());
    when(userPositionMapper.selectList(any())).thenReturn(List.of());
    when(roleMapper.selectRoleCodesByUserId(1L)).thenReturn(List.of());
    when(menuService.getSidebarMenuTree()).thenReturn(List.of());
    TenantContext.setTenantId(9L);

    UserVO profile = service.getUserProfile();

    assertThat(profile.getPerms()).containsExactly("*");
    assertThat(TenantContext.requireTenantId()).isEqualTo(9L);
  }

    // ---------- helpers ----------

    private static SysUser user(Long userId, String userCode) {
        SysUser u = new SysUser();
        u.setUserId(userId);
        u.setUserCode(userCode);
        u.setStatus(1);
        return u;
    }

    private static UserCreateRequest createRequest(String userCode) {
        UserCreateRequest r = new UserCreateRequest();
        r.setUserCode(userCode);
        r.setUserName("新用户");
        r.setDeptId(10L);
        r.setPassword("deadbeef");
        return r;
    }

    private static UserUpdateRequest updateRequest() {
        UserUpdateRequest r = new UserUpdateRequest();
        r.setUserName("改名");
        r.setDeptId(10L);
        r.setStatus(1);
        r.setVersion(0);
        return r;
    }

    private static ResetPasswordRequest resetRequest(String password) {
        ResetPasswordRequest r = new ResetPasswordRequest();
        r.setPassword(password);
        return r;
    }

    private static void assertBusinessError(ThrowingCallable call, HttpStatus status, int code) {
        assertThatThrownBy(call)
            .isInstanceOfSatisfying(BusinessException.class, ex -> {
                assertThat(ex.getStatus()).isEqualTo(status);
                assertThat(ex.getCode()).isEqualTo(code);
            });
    }
}

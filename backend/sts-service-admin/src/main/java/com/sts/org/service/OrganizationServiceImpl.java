package com.sts.org.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sts.admin.entity.*;
import com.sts.admin.cache.CacheEvictService;
import com.sts.admin.mapper.*;
import com.sts.admin.tenant.TenantContext;
import com.sts.common.api.PageRequest;
import com.sts.common.api.PageResult;
import com.sts.common.cache.CacheDomain;
import com.sts.common.cache.ClearScope;
import com.sts.common.exception.BusinessException;
import com.sts.org.dto.OrganizationCreateRequest;
import com.sts.org.dto.OrganizationQuery;
import com.sts.org.dto.OrganizationUpdateRequest;
import com.sts.org.entity.Organization;
import com.sts.org.mapper.OrganizationMapper;
import com.sts.org.vo.OrganizationVO;
import com.sts.regulator.service.RegulatorRolePermissions;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Objects;
import java.util.Set;

/**
 * 租户主数据管理实现。
 * <p>
 * 租户是扁平的，没有上下级；数据可见性由租户隔离保证，因此本服务不再解析角色数据范围。
 * 详见 openspec/changes/2026-08-06-rebuild-multi-tenant-isolation/design.md §1、§6.3。
 */
@Service
@RequiredArgsConstructor
public class OrganizationServiceImpl implements OrganizationService {

  private static final String SUPER_ADMIN_CODE = "superAdmin";
  private static final String TYPE_ENTERPRISE = TenantDirectory.TYPE_ENTERPRISE;
  private static final String TYPE_TRAINING_INSTITUTION = TenantDirectory.TYPE_TRAINING_INSTITUTION;
  private static final String TYPE_REGULATOR = TenantDirectory.TYPE_REGULATOR;
  private static final Set<String> ORG_TYPES = Set.of(TYPE_ENTERPRISE, TYPE_TRAINING_INSTITUTION, TYPE_REGULATOR);
  /** 企业和培训机构管理员不得获得租户主数据管理入口及按钮权限。 */
  private static final Set<Long> TENANT_ADMIN_EXCLUDED_MENU_IDS = Set.of(9532L, 1381L, 1382L, 1383L);
  private static final String ORG_CODE_PATTERN = "[A-Z0-9_-]{2,64}";

  private final OrganizationMapper organizationMapper;
  private final SysUserMapper userMapper;
  private final SysRoleMapper roleMapper;
  private final SysUserRoleMapper userRoleMapper;
  private final SysRoleMenuMapper roleMenuMapper;
  private final SysMenuMapper menuMapper;
  private final SysAuthIdentityMapper authIdentityMapper;
  private final PasswordEncoder passwordEncoder;
  private final CacheEvictService cacheEvictService;

  @Override
  public PageResult<OrganizationVO> pageOrganizations(Long userId, PageRequest<OrganizationQuery> request) {
    boolean superAdmin = isSuperAdmin(userId);
    requireOrgManager(userId, superAdmin);
    OrganizationQuery query = request.getQuery();
    LambdaQueryWrapper<Organization> wrapper = new LambdaQueryWrapper<>();
    if (!superAdmin) {
      wrapper.in(Organization::getOrgType, TenantDirectory.REGULATED_TYPES);
    }
    if (query != null) {
      wrapper.and(StringUtils.hasText(query.getKeyword()), nested -> nested
          .like(Organization::getOrgCode, query.getKeyword().trim())
          .or()
          .like(Organization::getOrgName, query.getKeyword().trim()))
        .eq(StringUtils.hasText(query.getOrgType()), Organization::getOrgType, query.getOrgType())
        .eq(query.getStatus() != null, Organization::getStatus, query.getStatus());
    }
    wrapper.orderByAsc(Organization::getOrgName);
    Page<Organization> page = organizationMapper.selectPage(
      new Page<>(request.getPageNum(), request.getPageSize()), wrapper);
    return new PageResult<>(page.getRecords().stream().map(this::toVO).toList(), page.getTotal());
  }

  @Override
  public OrganizationVO getOrganization(Long userId, Long organizationId) {
    boolean superAdmin = isSuperAdmin(userId);
    requireOrgManager(userId, superAdmin);
    Organization organization = requireOrganization(organizationId);
    assertRegulatedVisible(superAdmin, organization);
    return toVO(organization);
  }

  @Override
  @Transactional
  public Long createOrganization(Long userId, OrganizationCreateRequest request) {
    boolean superAdmin = isSuperAdmin(userId);
    requireOrgManager(userId, superAdmin);
    String orgCode = request.getOrgCode().trim().toUpperCase();
    if (!orgCode.matches(ORG_CODE_PATTERN)) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 1001, "企业编码格式不合法");
    }
    if (!ORG_TYPES.contains(request.getOrgType())) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 4042, "租户类型非法");
    }
    if (TYPE_REGULATOR.equals(request.getOrgType())) {
      throw new BusinessException(HttpStatus.FORBIDDEN, 4048, "监管平台租户只能由初始化建立");
    }
    assertCodeUnique(orgCode);
    validateContact(request.getContactUserId());

    Organization organization = new Organization();
    organization.setOrgCode(orgCode);
    organization.setOrgName(request.getOrgName().trim());
    organization.setOrgType(request.getOrgType());
    organization.setContactUserId(request.getContactUserId());
    organization.setStatus(request.getStatus() == null ? 1 : request.getStatus());
    organization.setDescription(request.getDescription() == null ? "" : request.getDescription().trim());
    try {
      organizationMapper.insert(organization);
    } catch (DuplicateKeyException ex) {
      throw new BusinessException(HttpStatus.CONFLICT, 4041, "企业编码已存在");
    }
    createTenantAdmin(organization, request);
    return organization.getOrganizationId();
  }

  @Override
  @Transactional
  public void updateOrganization(Long userId, Long organizationId, OrganizationUpdateRequest request) {
    boolean superAdmin = isSuperAdmin(userId);
    requireOrgManager(userId, superAdmin);
    Organization organization = requireOrganization(organizationId);
    assertRegulatedVisible(superAdmin, organization);
    validateContact(request.getContactUserId());
    if (StringUtils.hasText(request.getOrgType())) {
      applyOrgTypeChange(organization, request.getOrgType());
    }
    organization.setVersion(request.getVersion());
    organization.setOrgName(request.getOrgName().trim());
    organization.setContactUserId(request.getContactUserId());
    organization.setStatus(request.getStatus());
    organization.setDescription(request.getDescription() == null ? "" : request.getDescription().trim());
    organizationMapper.updateByIdWithVersionCheck(organization);
    // 机构名称参与展示翻译（如「所属机构」列），改名后必须失效单条展示名缓存。
    cacheEvictService.scheduleEvict(CacheDomain.ORGANIZATION, ClearScope.ENTRY, String.valueOf(organizationId));
  }

  /**
   * 为监管内置角色绑定固定权限集（design §4.5）。
   * <p>
   * 监管租户的屏蔽手段就是"不给业务写权限码"，因此这里 MUST 按
   * {@link RegulatorRolePermissions} 精确绑定，不能图省事把全部菜单授给它。
   * 菜单为全局定义（租户插件豁免表），按 perms 直接查即可。
   */
  private void bindAdminPermissions(Long roleId, boolean regulator) {
    LambdaQueryWrapper<SysMenu> query = new LambdaQueryWrapper<>();
    if (regulator) {
      query.in(SysMenu::getPerms, RegulatorRolePermissions.all());
    } else {
      // 新开企业不得继承旧监管模块和当前园区监管模块的菜单权限。
      query.eq(SysMenu::getIsVisible, 1).notIn(SysMenu::getModuleId, 950L, 9810L);
    }
    for (SysMenu menu : menuMapper.selectList(query)) {
      // 档案历史更正与留痕必须人工授权，开户不自动绑定。
      if ("archive:history:correct".equals(menu.getPerms()) || "archive:history:correction:list".equals(menu.getPerms())) continue;
      if (!regulator && TENANT_ADMIN_EXCLUDED_MENU_IDS.contains(menu.getMenuId())) {
        continue;
      }
      SysRoleMenu roleMenu = new SysRoleMenu();
      roleMenu.setRoleId(roleId);
      roleMenu.setMenuId(menu.getMenuId());
      roleMenuMapper.insert(roleMenu);
    }
  }

  /**
   * 企业开户只创建一个可登录的管理员及其必要授权，不代建企业内部数据。
   * <p>
   * 管理员用户与其技术性管理员角色在新租户上下文中写入，由租户插件填充归属；不创建默认部门、
   * 岗位、标签、其它账号或业务数据。凭证标识使用 {@code {企业编码}:{工号}} 复合值。
   */
  private void createTenantAdmin(Organization organization, OrganizationCreateRequest request) {
    Long organizationId = organization.getOrganizationId();
    TenantContext.runWithTenant(organizationId, () -> createTenantAdminInContext(organization, request));
  }

  private void createTenantAdminInContext(Organization organization,
                                          OrganizationCreateRequest request) {
    SysRole adminRole = new SysRole();
    adminRole.setRoleName("管理员");
    adminRole.setRoleCode("admin");
    adminRole.setRoleLevel("ADMIN");
    adminRole.setIsBuiltin(1);
    adminRole.setStatus(1);
    boolean regulator = TYPE_REGULATOR.equals(organization.getOrgType());
    adminRole.setDescription(regulator ? "监管平台内置角色" : "租户内置管理员");
    roleMapper.insert(adminRole);
    bindAdminPermissions(adminRole.getRoleId(), regulator);

    String adminUserCode = StringUtils.hasText(request.getAdminUserCode())
      ? request.getAdminUserCode().trim() : "admin";
    SysUser admin = new SysUser();
    admin.setUserCode(adminUserCode);
    admin.setUserName(StringUtils.hasText(request.getAdminUserName())
                        ? request.getAdminUserName().trim() : "管理员");
    admin.setMemberType("INTERNAL");
    admin.setStatus(1);
    userMapper.insert(admin);

    SysUserRole userRole = new SysUserRole();
    userRole.setUserId(admin.getUserId());
    userRole.setRoleId(adminRole.getRoleId());
    userRoleMapper.insert(userRole);

    SysAuthIdentity identity = new SysAuthIdentity();
    identity.setUserId(admin.getUserId());
    identity.setIdentityType("local");
    identity.setIdentifier(organization.getOrgCode() + ":" + adminUserCode);
    identity.setCredential(passwordEncoder.encode(request.getAdminPassword()));
    authIdentityMapper.insert(identity);
  }

  /**
   * 租户主数据由 superAdmin 或监管租户管理员维护。监管不得创建或改写监管平台租户。
   */
  private void requireOrgManager(Long userId, boolean superAdmin) {
    if (superAdmin) {
      return;
    }
    if (isRegulatorUser(userId)) {
      return;
    }
    throw new BusinessException(HttpStatus.FORBIDDEN, 4048, "仅系统管理员或监管租户可管理租户");
  }

  private boolean isSuperAdmin(Long userId) {
    SysUser user = userId == null ? null
      : TenantContext.callWithBypass(() -> userMapper.selectById(userId));
    return user != null && Objects.equals(user.getStatus(), 1)
      && SUPER_ADMIN_CODE.equals(user.getUserCode());
  }

  private boolean isRegulatorUser(Long userId) {
    SysUser user = userId == null ? null
      : TenantContext.callWithBypass(() -> userMapper.selectById(userId));
    if (user == null || !Objects.equals(user.getStatus(), 1) || user.getOrganizationId() == null) {
      return false;
    }
    Organization org = TenantContext.callWithBypass(() -> organizationMapper.selectById(user.getOrganizationId()));
    return org != null && TYPE_REGULATOR.equals(org.getOrgType()) && Objects.equals(org.getStatus(), 1);
  }

  private void assertRegulatedVisible(boolean superAdmin, Organization organization) {
    if (superAdmin) {
      return;
    }
    if (!TenantDirectory.isRegulatedOrgType(organization.getOrgType())) {
      throw new BusinessException(HttpStatus.NOT_FOUND, 4040, "租户不存在");
    }
  }

  /**
   * 仅允许企业与培训机构互转，不得推断、不得改写监管平台类型。
   */
  private void applyOrgTypeChange(Organization organization, String orgType) {
    if (TYPE_REGULATOR.equals(organization.getOrgType())) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 4042, "监管平台租户类型不可修改");
    }
    if (!TenantDirectory.isRegulatedOrgType(orgType)) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 4042, "租户类型非法");
    }
    organization.setOrgType(orgType);
  }

  private void assertCodeUnique(String orgCode) {
    if (organizationMapper.selectByOrgCode(orgCode) != null) {
      throw new BusinessException(HttpStatus.CONFLICT, 4041, "企业编码已存在");
    }
  }

  private void validateContact(Long contactUserId) {
    if (contactUserId == null) {
      return;
    }
    SysUser user = userMapper.selectById(contactUserId);
    if (user == null || !Objects.equals(user.getStatus(), 1)) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 4047, "联系人无效或已停用");
    }
  }

  private Organization requireOrganization(Long organizationId) {
    Organization organization = organizationMapper.selectById(organizationId);
    if (organization == null) {
      throw new BusinessException(HttpStatus.NOT_FOUND, 4040, "租户不存在");
    }
    return organization;
  }

  private OrganizationVO toVO(Organization organization) {
    OrganizationVO vo = new OrganizationVO();
    vo.setOrganizationId(organization.getOrganizationId());
    vo.setOrgCode(organization.getOrgCode());
    vo.setOrgName(organization.getOrgName());
    vo.setOrgType(organization.getOrgType());
    vo.setContactUserId(organization.getContactUserId());
    vo.setStatus(organization.getStatus());
    vo.setDescription(organization.getDescription());
    vo.setVersion(organization.getVersion());
    vo.setCreateTime(organization.getCreateTime());
    vo.setUpdateTime(organization.getUpdateTime());
    return vo;
  }
}

package com.sts.admin.system.service;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sts.admin.cache.CacheEvictService;
import com.sts.admin.entity.*;
import com.sts.admin.mapper.*;
import com.sts.admin.system.converter.SysUserConverter;
import com.sts.admin.system.dto.ResetPasswordRequest;
import com.sts.admin.system.dto.UserCreateRequest;
import com.sts.admin.system.dto.UserQuery;
import com.sts.admin.system.dto.UserUpdateRequest;
import com.sts.admin.system.vo.UserVO;
import com.sts.admin.tenant.CurrentTenantResolver;
import com.sts.admin.tenant.TenantContext;
import com.sts.admin.tenant.TenantSessionKeys;
import com.sts.common.api.PageRequest;
import com.sts.common.api.PageResult;
import com.sts.common.cache.CacheDomain;
import com.sts.common.cache.ClearScope;
import com.sts.common.exception.BusinessException;
import com.sts.common.service.BaseServiceImpl;
import com.sts.org.entity.OrgUserPosition;
import com.sts.org.entity.OrgUserTag;
import com.sts.org.entity.Organization;
import com.sts.org.mapper.OrgUserPositionMapper;
import com.sts.org.mapper.OrgUserTagMapper;
import com.sts.org.mapper.OrganizationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class SysUserServiceImpl extends BaseServiceImpl<SysUserMapper, SysUser> implements SysUserService {

    private final SysUserMapper userMapper;
    private final SysAuthIdentityMapper authIdentityMapper;
    private final SysAuthIdentityService authIdentityService;
    private final SysUserRoleMapper userRoleMapper;
    private final SysDeptService deptService;
  private final SysDeptMapper deptMapper;
    private final PasswordEncoder passwordEncoder;
    private final CacheEvictService cacheEvictService;
    private final SysUserCacheService userCacheService;
    private final SysUserConverter userConverter;
    private final SysMenuMapper menuMapper;
    private final SysRoleMapper roleMapper;
    private final SysMenuService menuService;
  private final OrganizationMapper organizationMapper;
  private final CurrentTenantResolver currentTenantResolver;
  private final OrgUserTagMapper userTagMapper;
  private final OrgUserPositionMapper userPositionMapper;

    @Override
    public PageResult<UserVO> pageUsers(PageRequest<UserQuery> request) {
        UserQuery query = request.getQuery();
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        if (query != null) {
            wrapper.like(StringUtils.hasText(query.getUserCode()), SysUser::getUserCode, query.getUserCode())
                .like(StringUtils.hasText(query.getUserName()), SysUser::getUserName, query.getUserName())
                .eq(StringUtils.hasText(query.getPhone()), SysUser::getPhone, query.getPhone())
                .eq(query.getStatus() != null, SysUser::getStatus, query.getStatus());

            if (query.getDeptId() != null) {
                List<Long> deptIds = deptService.listDescendantIds(query.getDeptId());
                if (deptIds.isEmpty()) {
                    return new PageResult<>(List.of(), 0);
                }
                wrapper.in(SysUser::getDeptId, deptIds);
            }
        }

        wrapper.orderByDesc(SysUser::getCreateTime);
        Page<SysUser> page = userMapper.selectPage(
            new Page<>(request.getPageNum(), request.getPageSize()),
            wrapper);
        List<UserVO> list = page.getRecords().stream().map(this::toVO).toList();
        return new PageResult<>(list, page.getTotal());
    }

    @Override
    @Transactional
    public Long createUser(UserCreateRequest request) {
        if ("superAdmin".equals(request.getUserCode())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, 2019, "系统根基特权，禁止物理创建");
        }
      Long organizationId = currentTenantResolver.resolve(StpUtil.getLoginIdAsLong());
      // 工号在租户内唯一：租户条件由插件注入，这里只需按工号计数
        Long count = userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                                                .eq(SysUser::getUserCode, request.getUserCode()));
        if (count != null && count > 0) {
            throw new BusinessException(HttpStatus.CONFLICT, 2001, "用户编码已存在");
        }
      assertDeptInTenant(request.getDeptId(), organizationId);
      assertRolesInTenant(request.getRoleIds(), organizationId);
      assertPrimaryPositionIncluded(request.getPrimaryPositionId(), request.getPositionIds());

        SysUser user = userConverter.toEntity(request);
      user.setOrganizationId(organizationId);
        userMapper.insert(user);

        SysAuthIdentity identity = new SysAuthIdentity();
        identity.setUserId(user.getUserId());
        identity.setIdentityType("local");
      // 工号只在租户内唯一，凭证标识须用 {企业编码}:{工号} 复合值保证全局唯一
      identity.setIdentifier(buildIdentifier(organizationId, request.getUserCode()));
        identity.setCredential(passwordEncoder.encode(request.getPassword()));
        authIdentityMapper.insert(identity);

        bindRoles(user.getUserId(), request.getRoleIds());
      bindTags(user.getUserId(), request.getTagIds());
      bindPositions(user.getUserId(), request.getPositionIds(), request.getPrimaryPositionId());
        return user.getUserId();
    }

    @Override
    @Transactional
    public void updateUser(Long userId, UserUpdateRequest request) {
        SysUser user = requireUser(userId);
        if ("superAdmin".equals(user.getUserCode())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, 2017, "系统根基特权，禁止物理变更");
        }
      Long organizationId = user.getOrganizationId();
      assertDeptInTenant(request.getDeptId(), organizationId);
      assertRolesInTenant(request.getRoleIds(), organizationId);
      assertPrimaryPositionIncluded(request.getPrimaryPositionId(), request.getPositionIds());

        user.setVersion(request.getVersion());
        userConverter.updateEntity(user, request);
        userMapper.updateByIdWithVersionCheck(user);

        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId));
        bindRoles(userId, request.getRoleIds());
      userTagMapper.delete(new LambdaQueryWrapper<OrgUserTag>().eq(OrgUserTag::getUserId, userId));
      bindTags(userId, request.getTagIds());
      userPositionMapper.delete(
        new LambdaQueryWrapper<OrgUserPosition>().eq(OrgUserPosition::getUserId, userId));
      bindPositions(userId, request.getPositionIds(), request.getPrimaryPositionId());

        userCacheService.evict(userId);
        // 👑 注意：用户角色变更后，前端权限缓存需要刷新
        cacheEvictService.scheduleEvict(CacheDomain.USER, ClearScope.ENTRY, String.valueOf(userId));
        cacheEvictService.scheduleEvict(CacheDomain.USER_CODE, ClearScope.ENTRY, user.getUserCode());
    }

    @Override
    @Transactional
    public void deleteUser(Long userId) {
        SysUser user = requireUser(userId);
        if ("superAdmin".equals(user.getUserCode())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, 2018, "系统根基特权，禁止物理删除");
        }
        // 👑 铁律执行：改用 this.removeById，实现 deleted = userId
        this.removeById(userId);

        authIdentityService.deleteByUserId(userId);
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId));
      userTagMapper.delete(new LambdaQueryWrapper<OrgUserTag>().eq(OrgUserTag::getUserId, userId));
      userPositionMapper.delete(
        new LambdaQueryWrapper<OrgUserPosition>().eq(OrgUserPosition::getUserId, userId));

        userCacheService.evict(userId);
        cacheEvictService.scheduleEvict(CacheDomain.USER, ClearScope.ENTRY, String.valueOf(userId));
        cacheEvictService.scheduleEvict(CacheDomain.USER_CODE, ClearScope.ENTRY, user.getUserCode());
    }

    @Override
    @Transactional
    public void resetPassword(Long userId, ResetPasswordRequest request) {
        SysUser user = requireUser(userId);
        if ("superAdmin".equals(user.getUserCode())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, 2019, "系统根基特权，禁止修改密码");
        }
        SysAuthIdentity identity = authIdentityMapper.selectOne(new LambdaQueryWrapper<SysAuthIdentity>()
                                                                    .eq(SysAuthIdentity::getUserId, userId)
                                                                    .eq(SysAuthIdentity::getIdentityType, "local"));
        if (identity == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, 2002, "用户未绑定本地账密凭证");
        }
        identity.setCredential(passwordEncoder.encode(request.getPassword()));
        authIdentityMapper.updateById(identity);
    }

    @Override
    public UserVO getUserProfile() {
        Long userId = StpUtil.getLoginIdAsLong();
      // 当前登录主体可能是归属其它租户的唯一 superAdmin；只按 token 主键读取自身身份。
      SysUser user = TenantContext.callWithBypass(() -> requireUser(userId));
        UserVO vo = toVO(user);

        // 实时获取最新的角色、权限点及侧边栏菜单树
        vo.setRoles(roleMapper.selectRoleCodesByUserId(userId));
        vo.setMenus(menuService.getSidebarMenuTree());
        // 会话租户类型决定导航渠道（监管会话不进入业务模块），只按当前会话回填，不代表账号归属。
        SaSession tokenSession = StpUtil.getTokenSession();
        vo.setOrgType(tokenSession == null ? null : tokenSession.getString(TenantSessionKeys.ORG_TYPE));

        // 特殊处理超级管理员身份
        if ("superAdmin".equals(user.getUserCode())) {
            vo.setPerms(List.of("*"));
        } else {
            vo.setPerms(menuMapper.selectPermsByUserId(userId));
        }

        return vo;
    }

    private void bindRoles(Long userId, List<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return;
        }
        for (Long roleId : roleIds) {
            SysUserRole userRole = new SysUserRole();
            userRole.setUserId(userId);
            userRole.setRoleId(roleId);
            userRoleMapper.insert(userRole);
        }
    }

  /**
   * 凭证标识 = 企业编码 + ':' + 租户内工号，与 {@code LocalLoginStrategy} 的拼装规则保持一致。
   */
  private String buildIdentifier(Long organizationId, String userCode) {
    Organization organization = organizationMapper.selectById(organizationId);
    if (organization == null) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 4040, "租户不存在");
    }
    return organization.getOrgCode() + ":" + userCode.trim();
  }

  private void assertDeptInTenant(Long deptId, Long organizationId) {
    if (deptId == null) {
      return;
    }
    SysDept dept = deptMapper.selectById(deptId);
    if (dept == null || !Objects.equals(dept.getOrganizationId(), organizationId)) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 2051, "所属部门不属于当前租户");
    }
  }

  private void assertRolesInTenant(List<Long> roleIds, Long organizationId) {
    if (roleIds == null || roleIds.isEmpty()) {
      return;
    }
    List<SysRole> roles = roleMapper.selectList(
      new LambdaQueryWrapper<SysRole>().in(SysRole::getRoleId, roleIds));
    if (roles.size() != roleIds.stream().distinct().count()
      || roles.stream().anyMatch(r -> !Objects.equals(r.getOrganizationId(), organizationId))) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 2052, "绑定角色不属于当前租户");
    }
  }

  private void assertPrimaryPositionIncluded(Long primaryPositionId, List<Long> positionIds) {
    if (primaryPositionId == null) {
      return;
    }
    if (positionIds == null || !positionIds.contains(primaryPositionId)) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 4023, "主岗位必须包含在岗位列表中");
    }
  }

  private void bindTags(Long userId, List<Long> tagIds) {
    if (tagIds == null || tagIds.isEmpty()) {
      return;
    }
    for (Long tagId : tagIds.stream().distinct().toList()) {
      OrgUserTag link = new OrgUserTag();
      link.setUserId(userId);
      link.setTagId(tagId);
      userTagMapper.insert(link);
    }
  }

  private void bindPositions(Long userId, List<Long> positionIds, Long primaryPositionId) {
    if (positionIds == null || positionIds.isEmpty()) {
      return;
    }
    for (Long positionId : positionIds.stream().distinct().toList()) {
      OrgUserPosition link = new OrgUserPosition();
      link.setUserId(userId);
      link.setPositionId(positionId);
      link.setIsPrimary(Objects.equals(positionId, primaryPositionId) ? 1 : 0);
      userPositionMapper.insert(link);
    }
  }

    private SysUser requireUser(Long userId) {
        SysUser user = userCacheService.getById(userId);
        if (user == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, 2004, "用户不存在");
        }
        return user;
    }

    private UserVO toVO(SysUser user) {
        UserVO vo = userConverter.toVO(user);
        vo.setRoleIds(userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                                                    .eq(SysUserRole::getUserId, user.getUserId()))
                          .stream()
                          .map(SysUserRole::getRoleId)
                          .filter(Objects::nonNull)
                          .toList());
      vo.setTagIds(userTagMapper.selectList(new LambdaQueryWrapper<OrgUserTag>()
                                              .eq(OrgUserTag::getUserId, user.getUserId()))
                     .stream().map(OrgUserTag::getTagId).filter(Objects::nonNull).toList());
      List<OrgUserPosition> positions = userPositionMapper.selectList(
        new LambdaQueryWrapper<OrgUserPosition>().eq(OrgUserPosition::getUserId, user.getUserId()));
      vo.setPositionIds(positions.stream().map(OrgUserPosition::getPositionId)
                          .filter(Objects::nonNull).toList());
      vo.setPrimaryPositionId(positions.stream()
                                .filter(pos -> Objects.equals(pos.getIsPrimary(), 1))
                                .map(OrgUserPosition::getPositionId)
                                .findFirst().orElse(null));
      return vo;
    }
}

package com.sts.admin.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sts.admin.cache.CacheEvictService;
import com.sts.admin.entity.SysDept;
import com.sts.admin.entity.SysRole;
import com.sts.admin.entity.SysRoleMenu;
import com.sts.admin.entity.SysUserRole;
import com.sts.admin.mapper.SysDeptMapper;
import com.sts.admin.mapper.SysRoleMapper;
import com.sts.admin.mapper.SysRoleMenuMapper;
import com.sts.admin.mapper.SysUserRoleMapper;
import com.sts.admin.system.converter.SysRoleConverter;
import com.sts.admin.system.dto.RoleCreateRequest;
import com.sts.admin.system.dto.RoleQuery;
import com.sts.admin.system.dto.RoleUpdateRequest;
import com.sts.admin.system.vo.RoleVO;
import com.sts.admin.tenant.TenantContext;
import com.sts.common.api.PageRequest;
import com.sts.common.api.PageResult;
import com.sts.common.cache.CacheDomain;
import com.sts.common.cache.ClearScope;
import com.sts.common.exception.BusinessException;
import com.sts.common.service.BaseServiceImpl;
import com.sts.org.entity.Organization;
import com.sts.org.mapper.OrganizationMapper;
import com.sts.org.service.TenantDirectory;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SysRoleServiceImpl extends BaseServiceImpl<SysRoleMapper, SysRole> implements SysRoleService {

    private static final Set<String> ROLE_LEVELS = Set.of("ADMIN", "TEACHER", "STUDENT");

    /**
     * 租户主数据菜单：机构管理（9532）及其查询/新增/编辑按钮（1381/1382/1383）。
     * <p>
     * 机构是租户主数据本身，只由 superAdmin 或监管租户维护（{@code OrganizationServiceImpl#requireOrgManager}），
     * 企业与培训机构角色持有后点击必然 403 / 4048。角色编辑弹窗里「机构组织」（500）与其同级，
     * 勾选目录会级联勾上 9532，因此必须在此兜底剔除，否则解绑后会被一次保存原样写回。
     */
    private static final Set<Long> TENANT_MASTER_DATA_MENU_IDS = Set.of(9532L, 1381L, 1382L, 1383L);

    private final SysRoleMapper roleMapper;
    private final SysRoleMenuMapper roleMenuMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysDeptMapper deptMapper;
    private final OrganizationMapper organizationMapper;
    private final CacheEvictService cacheEvictService;
    private final SysUserCacheService userCacheService;
    private final SysRoleConverter roleConverter;

    @Override
    public PageResult<RoleVO> pageRoles(PageRequest<RoleQuery> request) {
        RoleQuery query = request.getQuery();
        LambdaQueryWrapper<SysRole> wrapper = new LambdaQueryWrapper<>();
        wrapper.ne(SysRole::getRoleCode, "superAdmin");

        if (query != null) {
            wrapper.like(StringUtils.hasText(query.getRoleName()), SysRole::getRoleName, query.getRoleName())
                .like(StringUtils.hasText(query.getRoleCode()), SysRole::getRoleCode, query.getRoleCode())
                .eq(query.getStatus() != null, SysRole::getStatus, query.getStatus());
        }
        wrapper.orderByDesc(SysRole::getCreateTime);

        Page<SysRole> page = roleMapper.selectPage(
            new Page<>(request.getPageNum(), request.getPageSize()),
            wrapper);
        List<RoleVO> list = page.getRecords().stream().map(this::toVO).toList();
        return new PageResult<>(list, page.getTotal());
    }

    @Override
    public RoleVO getRole(Long roleId) {
        return toVO(requireRole(roleId));
    }

    @Override
    @Transactional
    public Long createRole(RoleCreateRequest request) {
        if ("superAdmin".equals(request.getRoleCode())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, 2014, "系统根基特权，禁止物理创建");
        }
        // 角色归属以当前会话租户为准，拒绝客户端指定其他机构。
        Long tenantId = TenantContext.currentTenantIdOrNull();
        if (tenantId != null && !tenantId.equals(request.getOrganizationId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, 1003, "只能在当前机构新增角色");
        }
        requireOrganization(request.getOrganizationId());
        validateRoleLevel(request.getRoleLevel());
        assertRoleCodeUnique(request.getOrganizationId(), request.getRoleCode(), null);

        SysRole role = roleConverter.toEntity(request);
        role.setOrganizationId(request.getOrganizationId());
        role.setRoleLevel(normalizeRoleLevel(request.getRoleLevel()));
        role.setIsBuiltin(0);
        role.setDescription(request.getDescription() == null ? "" : request.getDescription());
        roleMapper.insert(role);

        bindMenus(role.getRoleId(), request.getOrganizationId(), request.getMenuIds());
        return role.getRoleId();
    }

    @Override
    @Transactional
    public void updateRole(Long roleId, RoleUpdateRequest request) {
        SysRole role = requireRole(roleId);
        if ("superAdmin".equals(role.getRoleCode()) || "superAdmin".equals(request.getRoleCode())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, 2015, "系统根基特权，禁止物理变更");
        }
        validateRoleLevel(request.getRoleLevel());
        role.setVersion(request.getVersion());
        assertRoleCodeUnique(role.getOrganizationId(), request.getRoleCode(), roleId);

        roleConverter.updateEntity(role, request);
        role.setRoleLevel(normalizeRoleLevel(request.getRoleLevel()));
        role.setDescription(request.getDescription() == null ? "" : request.getDescription());
        roleMapper.updateByIdWithVersionCheck(role);

        bindMenus(roleId, role.getOrganizationId(), request.getMenuIds());

        cacheEvictService.scheduleEvict(CacheDomain.ROLE, ClearScope.ENTRY, String.valueOf(roleId));
        userCacheService.evictAllPerms();
    }

    @Override
    @Transactional
    public void deleteRole(Long roleId) {
        SysRole role = requireRole(roleId);
        if ("superAdmin".equals(role.getRoleCode())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, 2016, "系统根基特权，禁止物理删除");
        }
        Long boundCount = userRoleMapper.selectCount(
            new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getRoleId, roleId));
        if (boundCount != null && boundCount > 0) {
            throw new BusinessException(HttpStatus.CONFLICT, 2006, "角色已被用户绑定，无法删除");
        }

        this.removeById(roleId);

        roleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, roleId));
        cacheEvictService.scheduleEvict(CacheDomain.ROLE, ClearScope.ENTRY, String.valueOf(roleId));
    }

    private void assertRoleCodeUnique(Long organizationId, String roleCode, Long excludeRoleId) {
        LambdaQueryWrapper<SysRole> wrapper = new LambdaQueryWrapper<SysRole>()
            .eq(SysRole::getOrganizationId, organizationId)
            .eq(SysRole::getRoleCode, roleCode);
        if (excludeRoleId != null) {
            wrapper.ne(SysRole::getRoleId, excludeRoleId);
        }
        Long count = roleMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new BusinessException(HttpStatus.CONFLICT, 2007, "角色编码已存在");
        }
    }

    /**
     * 全删全插地重绑角色菜单。
     * <p>
     * 非监管租户角色一律剔除租户主数据菜单（{@link #TENANT_MASTER_DATA_MENU_IDS}）：
     * 前端勾选「机构组织」目录会级联带上「机构管理」，若不在此兜底，一次保存即可把已解绑的死入口写回。
     * 监管租户走 9810 模块下的 9814 独立入口，不受影响。
     */
    private void bindMenus(Long roleId, Long organizationId, List<Long> menuIds) {
        roleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, roleId));
        if (menuIds == null || menuIds.isEmpty()) {
            return;
        }
        boolean regulator = isRegulatorOrganization(organizationId);
        menuIds.stream()
            .filter(Objects::nonNull)
            .distinct()
            .filter(menuId -> regulator || !TENANT_MASTER_DATA_MENU_IDS.contains(menuId))
            .forEach(menuId -> {
                SysRoleMenu roleMenu = new SysRoleMenu();
                roleMenu.setRoleId(roleId);
                roleMenu.setMenuId(menuId);
                roleMenuMapper.insert(roleMenu);
            });
    }

    /** 角色所属租户是否为监管平台租户；租户不在册时按非监管处理，租户主数据菜单不予授予。 */
    private boolean isRegulatorOrganization(Long organizationId) {
        if (organizationId == null) {
            return false;
        }
        Organization organization = TenantContext.callWithBypass(
            () -> organizationMapper.selectById(organizationId));
        return organization != null && TenantDirectory.TYPE_REGULATOR.equals(organization.getOrgType());
    }

    private void requireOrganization(Long organizationId) {
        Organization org = organizationMapper.selectById(organizationId);
        if (org == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, 4003, "机构不存在");
        }
    }

    private void validateRoleLevel(String roleLevel) {
        String level = normalizeRoleLevel(roleLevel);
        if (!ROLE_LEVELS.contains(level)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, 2033, "角色级别无效");
        }
    }

    private String normalizeRoleLevel(String roleLevel) {
        return StringUtils.hasText(roleLevel) ? roleLevel : "ADMIN";
    }

    private SysRole requireRole(Long roleId) {
        SysRole role = roleMapper.selectById(roleId);
        if (role == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, 2008, "角色不存在");
        }
        return role;
    }

    private RoleVO toVO(SysRole role) {
        RoleVO vo = roleConverter.toVO(role);
        vo.setMenuIds(roleMenuMapper.selectList(new LambdaQueryWrapper<SysRoleMenu>()
                                                    .eq(SysRoleMenu::getRoleId, role.getRoleId()))
                          .stream()
                          .map(SysRoleMenu::getMenuId)
                          .filter(Objects::nonNull)
                          .toList());
        return vo;
    }
}

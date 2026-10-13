package com.sts.admin.tenant;

import com.sts.admin.entity.SysUser;
import com.sts.admin.mapper.SysUserMapper;
import com.sts.common.exception.BusinessException;
import com.sts.org.entity.Organization;
import com.sts.org.mapper.OrganizationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * 解析当前请求的租户 ID。
 * <p>
 * 绝大多数业务不需要它——租户条件由插件自动注入。只有确实要把租户写进业务字段
 * （如分片上传会话）或需要对租户本身做判断的场景才用。
 * <p>
 * 解析顺序：先取请求上下文；非 Web 场景没有上下文时才回落到账号自身的租户归属。
 */
@Service
@RequiredArgsConstructor
public class CurrentTenantResolver {

  private final SysUserMapper userMapper;
  private final OrganizationMapper organizationMapper;

  /**
   * 返回当前租户 ID；解析不出有效且启用的租户时抛出 403。
   */
  public Long resolve(Long userId) {
    Long fromContext = TenantContext.currentTenantIdOrNull();
    if (fromContext != null) {
      return requireEnabledTenant(fromContext);
    }
    // 非 Web 场景没有请求租户时，回落到账号归属。
    SysUser user = userId == null ? null : TenantContext.callWithBypass(() -> userMapper.selectById(userId));
    if (user == null || !Objects.equals(user.getStatus(), 1) || user.getOrganizationId() == null) {
      throw noTenant();
    }
    return requireEnabledTenant(user.getOrganizationId());
  }

  private Long requireEnabledTenant(Long organizationId) {
    Organization organization = TenantContext.callWithBypass(
      () -> organizationMapper.selectById(organizationId));
    if (organization == null || !Objects.equals(organization.getStatus(), 1)) {
      throw noTenant();
    }
    return organizationId;
  }

  private static BusinessException noTenant() {
    return new BusinessException(HttpStatus.FORBIDDEN, 4031, "当前用户没有有效租户归属");
  }
}

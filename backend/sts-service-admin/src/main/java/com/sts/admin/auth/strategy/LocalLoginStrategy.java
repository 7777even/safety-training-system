package com.sts.admin.auth.strategy;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sts.admin.entity.SysAuthIdentity;
import com.sts.admin.entity.SysUser;
import com.sts.admin.mapper.SysAuthIdentityMapper;
import com.sts.admin.mapper.SysUserMapper;
import com.sts.admin.tenant.TenantContext;
import com.sts.common.exception.BusinessException;
import com.sts.org.entity.Organization;
import com.sts.org.mapper.OrganizationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 企业编码 + 工号 + 密码登录。
 * <p>
 * 多租户下工号只在租户内唯一，因此凭证标识使用 {@code {orgCode}:{userCode}} 复合值，
 * {@code us_sys_auth_identity} 的全局唯一键由此天然满足租户内唯一。
 * <p>
 * 全部失败分支返回同一话术，调用方无法区分"企业编码不存在""账号不存在""密码错误"，
 * 避免枚举租户与账号。详见 design.md §5.1。
 */
@Component
@RequiredArgsConstructor
public class LocalLoginStrategy implements LoginStrategy {

  private static final String IDENTITY_TYPE = "local";
  private static final String SUPER_ADMIN_CODE = "superAdmin";
  /**
   * 防枚举：租户/账号/密码任一不对，一律返回这条话术。
   */
  private static final String FAIL_MESSAGE = "企业编码或账号密码错误";

  private final SysUserMapper userMapper;
  private final SysAuthIdentityMapper authIdentityMapper;
  private final OrganizationMapper organizationMapper;
  private final PasswordEncoder passwordEncoder;

  @Override
  public String getIdentityType() {
    return IDENTITY_TYPE;
  }

  @Override
  public AuthenticationResult authenticate(Map<String, String> params) {
    String orgCode = normalizeOrgCode(params.get("orgCode"));
    String userCode = params.get("userCode") == null ? null : params.get("userCode").trim();
    // 前端传入的是 SHA-256(明文) 的 64 位离散串
    String password = params.get("password");

    if (!StringUtils.hasText(orgCode) || !StringUtils.hasText(userCode)
      || !StringUtils.hasText(password)) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 1001, "企业编码、账号或密码不能为空");
    }

    // 1. 解析租户。租户不存在或已停用与密码错误返回同一话术，不泄露租户是否存在。
    Organization tenant = organizationMapper.selectByOrgCode(orgCode);
    if (tenant == null || !Objects.equals(tenant.getStatus(), 1)) {
      throw unauthorized();
    }

    SysUser user;
    SysAuthIdentity identity;
    if (SUPER_ADMIN_CODE.equals(userCode)) {
      // superAdmin 是唯一平台主体，凭证不复制到每个租户。目标企业编码只决定本次会话租户。
      List<SysUser> platformUsers = TenantContext.callWithBypass(() -> userMapper.selectList(
        new LambdaQueryWrapper<SysUser>().eq(SysUser::getUserCode, SUPER_ADMIN_CODE)));
      if (platformUsers == null || platformUsers.size() != 1) {
        throw unauthorized();
      }
      user = platformUsers.getFirst();
      identity = authIdentityMapper.selectOne(
        new LambdaQueryWrapper<SysAuthIdentity>()
          .eq(SysAuthIdentity::getIdentityType, IDENTITY_TYPE)
          .eq(SysAuthIdentity::getUserId, user.getUserId()));
    } else {
      // 普通账号仍按企业编码 + 租户内工号定位凭证。
      identity = authIdentityMapper.selectOne(
        new LambdaQueryWrapper<SysAuthIdentity>()
          .eq(SysAuthIdentity::getIdentityType, IDENTITY_TYPE)
          .eq(SysAuthIdentity::getIdentifier, buildIdentifier(orgCode, userCode)));
      user = null;
    }
    if (identity == null || identity.getCredential() == null) {
      throw unauthorized();
    }

    // 3. 校验密码（前端 SHA-256 + 后端 BCrypt 双层哈希）
    if (!passwordEncoder.matches(password, identity.getCredential())) {
      throw unauthorized();
    }

    // 普通账号需从凭证加载主体；superAdmin 主体已由唯一平台账号查询确定。
    if (user == null) {
      user = TenantContext.callWithBypass(() -> userMapper.selectById(identity.getUserId()));
    }
    if (user == null) {
      throw unauthorized();
    }
    // 凭证关联的主体必须与请求工号一致，防止异常关联把普通入口指向平台主体。
    if (!Objects.equals(user.getUserCode(), userCode)) {
      throw unauthorized();
    }
    // 普通账号必须归属目标租户；平台 superAdmin 只使用目标租户建立会话，不校验主体归属。
    if (!SUPER_ADMIN_CODE.equals(user.getUserCode())
      && !Objects.equals(user.getOrganizationId(), tenant.getOrganizationId())) {
      throw unauthorized();
    }
    if (user.getStatus() != null && user.getStatus() == 0) {
      throw new BusinessException(HttpStatus.FORBIDDEN, 1003, "您的账号已被禁用");
    }
    return new AuthenticationResult(user.getUserId(), tenant.getOrganizationId(), tenant.getOrgType());
  }

  /**
   * 凭证标识 = 企业编码 + ':' + 租户内工号。
   */
  public String buildIdentifier(String orgCode, String userCode) {
    return normalizeOrgCode(orgCode) + ":" + (userCode == null ? "" : userCode.trim());
  }

  private static String normalizeOrgCode(String orgCode) {
    return orgCode == null ? null : orgCode.trim().toUpperCase();
  }

  private static BusinessException unauthorized() {
    return new BusinessException(HttpStatus.UNAUTHORIZED, 1002, FAIL_MESSAGE);
  }
}

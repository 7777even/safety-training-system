package com.sts.admin.auth.strategy;

/**
 * 认证通过后的服务端身份结果。
 *
 * @param userId         用户主体 ID
 * @param organizationId 本次登录选择的租户 ID
 * @param orgType        本次登录选择的租户类型
 */
public record AuthenticationResult(Long userId, Long organizationId, String orgType) {
}

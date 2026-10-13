package com.sts.regulator.service;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import com.sts.admin.tenant.TenantSessionKeys;
import com.sts.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mockStatic;

/**
 * 跨租户读取入口的身份护栏。
 * <p>
 * 聚合通道与监管概览都绕过或跳出租户插件，这道断言是它们唯一的准入护栏。
 */
class RegulatorIdentityGuardTest {

  private final RegulatorIdentityGuard guard = new RegulatorIdentityGuard();

  /**
   * SaSession.getString 是 final 方法，无法 stub，这里用真实 Session 写值。
   */
  private static SaSession sessionWithOrgType(String orgType) {
    SaSession session = new SaSession("test-token");
    if (orgType != null) {
      session.set(TenantSessionKeys.ORG_TYPE, orgType);
    }
    return session;
  }

  @Test
  @DisplayName("未登录返回 401")
  void notLogin_returns401() {
    try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
      stp.when(StpUtil::isLogin).thenReturn(false);

      assertThatThrownBy(guard::requireRegulator)
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("code", 1002);
    }
  }

  @Test
  @DisplayName("企业租户调用跨租户读取返回 403")
  void enterpriseTenant_returns403() {
    try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
      stp.when(StpUtil::isLogin).thenReturn(true);
      stp.when(StpUtil::getTokenSession).thenReturn(sessionWithOrgType("ENTERPRISE"));

      assertThatThrownBy(guard::requireRegulator)
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("code", 4065);
    }
  }

  @Test
  @DisplayName("会话缺少租户类型（含 superAdmin 旁路）同样拒绝")
  void missingOrgType_returns403() {
    try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
      stp.when(StpUtil::isLogin).thenReturn(true);
      stp.when(StpUtil::getTokenSession).thenReturn(sessionWithOrgType(null));

      assertThatThrownBy(guard::requireRegulator)
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("code", 4065);
    }
  }

  @Test
  @DisplayName("监管租户放行")
  void regulatorTenant_isAllowed() {
    try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
      stp.when(StpUtil::isLogin).thenReturn(true);
      stp.when(StpUtil::getTokenSession).thenReturn(sessionWithOrgType("REGULATOR"));

      assertThatCode(guard::requireRegulator).doesNotThrowAnyException();
    }
  }
}

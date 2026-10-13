package com.sts.regulator.service;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import com.sts.admin.tenant.TenantSessionKeys;
import com.sts.common.exception.BusinessException;
import com.sts.org.service.TenantDirectory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * 跨租户读取入口的 {@code REGULATOR} 身份护栏。
 * <p>
 * 监管工作台、学习统计与预警列表会读到不止当前租户的数据，入口 MUST 先过这里。
 */
@Service
public class RegulatorIdentityGuard {

  /**
   * 仅 {@code REGULATOR} 租户放行。{@code superAdmin} 同样拒绝：
   * 它虽有全局旁路，但监管能力是业务能力而非运维能力，入口保持单一。
   */
  public void requireRegulator() {
    if (!StpUtil.isLogin()) {
      throw new BusinessException(HttpStatus.UNAUTHORIZED, 1002, "未登录");
    }
    SaSession session = StpUtil.getTokenSession();
    String orgType = session.getString(TenantSessionKeys.ORG_TYPE);
    if (!TenantDirectory.TYPE_REGULATOR.equals(orgType)) {
      throw new BusinessException(HttpStatus.FORBIDDEN, 4065,
                                  "跨租户读取仅监管平台租户可用");
    }
  }
}

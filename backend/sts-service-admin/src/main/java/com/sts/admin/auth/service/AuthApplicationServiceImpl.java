package com.sts.admin.auth.service;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import com.sts.admin.auth.config.AuthConfigProperties;
import com.sts.admin.auth.dto.LoginRequest;
import com.sts.admin.auth.dto.LoginVO;
import com.sts.admin.auth.event.LoginLogEvent;
import com.sts.admin.auth.strategy.AuthenticationResult;
import com.sts.admin.auth.strategy.LoginStrategy;
import com.sts.admin.auth.strategy.LoginStrategyFactory;
import com.sts.admin.entity.SysLoginLog;
import com.sts.admin.entity.SysUser;
import com.sts.admin.system.service.SysUserCacheService;
import com.sts.admin.system.service.SysUserOnlineService;
import com.sts.admin.tenant.TenantContext;
import com.sts.admin.tenant.TenantSessionKeys;
import com.sts.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthApplicationServiceImpl implements AuthApplicationService {

  private static final String SUPER_ADMIN_CODE = "superAdmin";

  /**
   * 租户无法解析的登录尝试（渠道禁用、账号不存在、密码错误）归入该值，仅 superAdmin 可见。
   */
  private static final Long UNKNOWN_TENANT = 0L;

  private final AuthConfigProperties authConfigProperties;
  private final LoginStrategyFactory loginStrategyFactory;
  private final SysUserCacheService userCacheService;
  private final ApplicationEventPublisher eventPublisher;

  @Override
  public LoginVO login(LoginRequest request, HttpServletRequest servletRequest) {
    String identityType = request.getIdentityType();
    String username = request.getAuthParams() != null ? request.getAuthParams().get("userCode") : null;

    List<String> enabledTypes = authConfigProperties.getEnabledTypes();
    if (!enabledTypes.contains(identityType)) {
      saveLoginLog(username, servletRequest, 0, "该登录渠道已被系统禁用", UNKNOWN_TENANT);
      throw new BusinessException(HttpStatus.FORBIDDEN, 1003, "当前登录渠道未开启: " + identityType);
    }

    try {
      LoginStrategy strategy = loginStrategyFactory.getStrategy(identityType);
      AuthenticationResult authentication = strategy.authenticate(request.getAuthParams());
      Long userId = authentication.userId();

      SysUser user = TenantContext.callWithBypass(() -> userCacheService.getById(userId));
      if (user == null) {
        throw new BusinessException(HttpStatus.UNAUTHORIZED, 1002, "用户主体不存在");
      }

      StpUtil.login(userId);
      fillOnlineSession(user, servletRequest);
      fillTenantSession(user, authentication);

      saveLoginLog(username, servletRequest, 1, "登录成功", authentication.organizationId());

      return new LoginVO(StpUtil.getTokenValue());
    } catch (BusinessException e) {
      saveLoginLog(username, servletRequest, 0, e.getMessage(), UNKNOWN_TENANT);
      throw e;
    } catch (Exception e) {
      saveLoginLog(username, servletRequest, 0, "系统异常: " + e.getMessage(), UNKNOWN_TENANT);
      throw e;
    }
  }

  /**
   * 在线用户信息写入 TokenSession（Redis），供在线用户管理页查询展示
   */
  private void fillOnlineSession(SysUser user, HttpServletRequest request) {
    SaSession tokenSession = StpUtil.getTokenSession();
    tokenSession.set(SysUserOnlineService.SESSION_KEY_USER_CODE, user.getUserCode());
    tokenSession.set(SysUserOnlineService.SESSION_KEY_USER_NAME, user.getUserName());
    tokenSession.set(SysUserOnlineService.SESSION_KEY_IP, request.getRemoteAddr());
    tokenSession.set(SysUserOnlineService.SESSION_KEY_BROWSER, resolveBrowser(request));
    tokenSession.set(SysUserOnlineService.SESSION_KEY_OS, "unknown");
    tokenSession.set(SysUserOnlineService.SESSION_KEY_LOCATION, "Local");
  }

  /**
   * 写入租户身份，供 {@link com.sts.admin.tenant.TenantContextFilter} 在后续请求中绑定租户上下文。
   * <p>
   * {@code superAdmin} 只保留功能特权，数据范围同样绑定本次登录选择的租户。
   */
  private void fillTenantSession(SysUser user, AuthenticationResult authentication) {
    SaSession tokenSession = StpUtil.getTokenSession();
    boolean superAdmin = SUPER_ADMIN_CODE.equals(user.getUserCode());
    tokenSession.set(TenantSessionKeys.SUPER_ADMIN, superAdmin);
    tokenSession.set(TenantSessionKeys.ORGANIZATION_ID, authentication.organizationId());
    tokenSession.set(TenantSessionKeys.ORG_TYPE, authentication.orgType());
  }

  private String resolveBrowser(HttpServletRequest request) {
    String userAgent = request.getHeader("User-Agent");
    if (userAgent == null) {
      return "unknown";
    }
    return userAgent.length() > 64 ? userAgent.substring(0, 64) : userAgent;
  }

  private void saveLoginLog(String username, HttpServletRequest request, int status, String msg,
                            Long organizationId) {
    SysLoginLog loginLog = new SysLoginLog();
    loginLog.setOrganizationId(organizationId == null ? UNKNOWN_TENANT : organizationId);
    loginLog.setUserCode(username != null ? username : "unknown");
    loginLog.setIpaddr(request.getRemoteAddr());
    loginLog.setLoginLocation("Local");
    loginLog.setBrowser(resolveBrowser(request));
    loginLog.setOs("unknown");
    loginLog.setStatus(status);
    loginLog.setMsg(msg != null ? msg : "");
    loginLog.setLoginTime(LocalDateTime.now());
    loginLog.setCreateTime(LocalDateTime.now());
    eventPublisher.publishEvent(new LoginLogEvent(this, loginLog));
  }
}

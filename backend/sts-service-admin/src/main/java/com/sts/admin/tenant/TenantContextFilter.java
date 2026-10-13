package com.sts.admin.tenant;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sts.common.api.Result;
import com.sts.common.exception.BusinessException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 每个 HTTP 请求绑定租户上下文，请求结束后清理。
 * <p>
 * 未登录请求（登录接口、公开文件下载等）不绑定租户；这些路径不得访问受隔离的业务表。
 * 已登录请求从 Sa-Token Session 读取本次登录租户；{@code superAdmin} 只保留功能权限，
 * 普通业务数据同样绑定登录租户。
 * <p>
 * 监管平台可通过 {@code X-Target-Tenant-Id} 切换查看视角：校验通过后把上下文绑定到
 * 目标企业租户，业务代码无需感知监管逻辑（design §4.2）。
 * <p>
 * 本过滤器优先级高于 Sa-Token 鉴权拦截器，保证进入业务代码前上下文已就绪；
 * {@code finally} 中的清理是防止线程池复用串租户的关键，MUST NOT 移除。
 * 详见 design.md §2.1。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class TenantContextFilter extends OncePerRequestFilter {

  private final RegulatorViewResolver regulatorViewResolver;
  private final RegulatorAccessAuditor regulatorAccessAuditor;
  private final ObjectMapper objectMapper;

  /**
   * ASYNC 二次分派运行在新的容器线程，不能复用首次 REQUEST 的 ThreadLocal。
   */
  @Override
  protected boolean shouldNotFilterAsyncDispatch() {
    return false;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                  FilterChain filterChain) throws ServletException, IOException {
    try {
      bindTenant(request);
    } catch (BusinessException ex) {
      // 过滤器早于 DispatcherServlet，异常不会经过 @RestControllerAdvice，必须自行写出统一响应
      TenantContext.clear();
      writeError(response, ex);
      return;
    }
    try {
      filterChain.doFilter(request, response);
    } finally {
      TenantContext.clear();
    }
  }

  private void bindTenant(HttpServletRequest request) {
    if (!StpUtil.isLogin()) {
      rejectViewSwitchIfPresent(request);
      return;
    }
    SaSession session = StpUtil.getTokenSession();
    boolean superAdmin = Boolean.TRUE.equals(session.get(TenantSessionKeys.SUPER_ADMIN));
    if (superAdmin) {
      // superAdmin 切换企业必须重新登录，不能用监管请求头覆盖会话租户。
      rejectViewSwitchIfPresent(request);
    }

    Object organizationId = session.get(TenantSessionKeys.ORGANIZATION_ID);
    if (!(organizationId instanceof Number number)) {
      // 改造前的旧 token 没有登录租户，必须直接失效，不能回退为全局旁路。
      rejectViewSwitchIfPresent(request);
      log.warn("登录会话缺少租户信息，userId={}", StpUtil.getLoginIdDefaultNull());
      throw new BusinessException(org.springframework.http.HttpStatus.UNAUTHORIZED, 1002,
                                  "登录状态已失效，请重新登录");
    }

    Long callerOrgId = number.longValue();
    String rawHeader = request.getHeader(RegulatorViewResolver.HEADER);
    if (!StringUtils.hasText(rawHeader)) {
      TenantContext.setTenantId(callerOrgId);
      return;
    }

    String callerOrgType = session.getString(TenantSessionKeys.ORG_TYPE);
    Long targetTenantId = regulatorViewResolver.resolveTargetTenant(
      rawHeader, callerOrgType, request.getMethod());
    TenantContext.setTenantId(targetTenantId);
    regulatorAccessAuditor.record(callerOrgId, targetTenantId,
                                  request.getMethod(), request.getRequestURI(), request.getRemoteAddr());
  }

  /**
   * 未登录、superAdmin 或会话无租户时提交切换视角头，一律拒绝，
   * MUST NOT 静默忽略后按原有身份继续执行。
   */
  private void rejectViewSwitchIfPresent(HttpServletRequest request) {
    if (StringUtils.hasText(request.getHeader(RegulatorViewResolver.HEADER))) {
      throw new BusinessException(org.springframework.http.HttpStatus.FORBIDDEN, 4061,
                                  "仅监管平台租户可切换查看视角");
    }
  }

  private void writeError(HttpServletResponse response, BusinessException ex) throws IOException {
    response.setStatus(ex.getStatus().value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response.getWriter().write(
      objectMapper.writeValueAsString(Result.fail(ex.getCode(), ex.getMessage())));
  }
}

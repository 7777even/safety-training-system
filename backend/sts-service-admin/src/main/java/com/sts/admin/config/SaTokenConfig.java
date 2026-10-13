package com.sts.admin.config;

import cn.dev33.satoken.filter.SaTokenContextFilterForJakartaServlet;
import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpUtil;
import com.sts.admin.tenant.TenantContextFilter;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class SaTokenConfig implements WebMvcConfigurer {
  private static final String AUTHENTICATED_REQUEST_ATTRIBUTE =
    SaTokenConfig.class.getName() + ".AUTHENTICATED";

  /**
   * Sa-Token 的请求上下文必须先于所有调用 StpUtil 的业务过滤器建立。
   */
  @Bean
  FilterRegistrationBean<SaTokenContextFilterForJakartaServlet> saTokenContextFilterRegistration(
    SaTokenContextFilterForJakartaServlet contextFilter) {
    FilterRegistrationBean<SaTokenContextFilterForJakartaServlet> registration =
      new FilterRegistrationBean<>(contextFilter);
    registration.setName("saTokenContextFilter");
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
    registration.setDispatcherTypes(
      DispatcherType.REQUEST, DispatcherType.ASYNC, DispatcherType.ERROR);
    return registration;
  }

  /**
   * Servlet ASYNC 二次分派会切换容器线程，必须重新从 TokenSession 绑定租户上下文。
   */
  @Bean
  FilterRegistrationBean<TenantContextFilter> tenantContextFilterRegistration(
    TenantContextFilter tenantContextFilter) {
    FilterRegistrationBean<TenantContextFilter> registration =
      new FilterRegistrationBean<>(tenantContextFilter);
    registration.setName("tenantContextFilter");
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
    registration.setAsyncSupported(true);
    registration.setDispatcherTypes(DispatcherType.REQUEST, DispatcherType.ASYNC);
    return registration;
  }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 注：由于 server.servlet.context-path 已配置为 /api，
        // Spring MVC 拦截器匹配的是 servlet path，其已被容器剔除掉了 /api 前缀。
        // 因此，拦截路径应配置为除去 /api 后的实际 Mapping。
      HandlerInterceptor authentication = new SaInterceptor(handle -> StpUtil.checkLogin()) {
        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                                 Object handler) throws Exception {
          // 错误页只负责呈现原始异常，不能再次鉴权并覆盖第一现场。
          if (request.getDispatcherType() == DispatcherType.ERROR) {
            return true;
          }
          // 仅当同一请求已通过首次鉴权时，才跳过 StreamingResponseBody 的 ASYNC 二次分派。
          if (request.getDispatcherType() == DispatcherType.ASYNC
            && Boolean.TRUE.equals(request.getAttribute(AUTHENTICATED_REQUEST_ATTRIBUTE))) {
            return true;
          }
          boolean allowed = super.preHandle(request, response, handler);
          if (allowed) {
            request.setAttribute(AUTHENTICATED_REQUEST_ATTRIBUTE, Boolean.TRUE);
          }
          return allowed;
        }
      };
      registry.addInterceptor(authentication)
            .addPathPatterns("/**")
            .excludePathPatterns(
                "/auth/login",
                "/auth/enabled-types",
                "/files/**",
              // OnlyOffice 使用一次性令牌回拉源文件，不能携带后台登录态。
                "/internal/asset/transcode-sources/**"
            );
    }
}

package com.sts.admin.config;

import cn.dev33.satoken.spring.SaTokenContextRegister;
import cn.dev33.satoken.stp.StpUtil;
import com.sts.admin.tenant.TenantContextFilter;
import jakarta.servlet.DispatcherType;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.core.Ordered;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.handler.MappedInterceptor;
import org.springframework.web.util.ServletRequestPathUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class SaTokenConfigTest {

  @Test
  void oneTimeTranscodeSourceRouteDoesNotRequireLogin() {
    MappedInterceptor interceptor = configuredInterceptor();

    assertThat(interceptor.matches(request("/internal/asset/transcode-sources/one-time-token")))
      .isFalse();
  }

  @Test
  void regularBusinessRouteStillRequiresLogin() {
    MappedInterceptor interceptor = configuredInterceptor();

    assertThat(interceptor.matches(request("/asset/assets"))).isTrue();
  }

  @Test
  void asyncRedispatchDoesNotRepeatSaTokenAuthentication() throws Exception {
    MappedInterceptor interceptor = configuredInterceptor();
    MockHttpServletRequest request = request("/asset/files/1/2");

    try (var stp = mockStatic(StpUtil.class)) {
      assertThat(interceptor.getInterceptor()
                   .preHandle(request, new MockHttpServletResponse(), new Object()))
        .isTrue();
      request.setDispatcherType(DispatcherType.ASYNC);
      assertThat(interceptor.getInterceptor()
                   .preHandle(request, new MockHttpServletResponse(), new Object()))
        .isTrue();
      stp.verify(StpUtil::checkLogin, times(1));
    }
  }

  @Test
  void asyncRequestWithoutInitialAuthenticationStillRequiresLogin() throws Exception {
    MappedInterceptor interceptor = configuredInterceptor();
    MockHttpServletRequest request = request("/asset/files/1/2");
    request.setDispatcherType(DispatcherType.ASYNC);

    try (var stp = mockStatic(StpUtil.class)) {
      assertThat(interceptor.getInterceptor()
                   .preHandle(request, new MockHttpServletResponse(), new Object()))
        .isTrue();
      stp.verify(StpUtil::checkLogin, times(1));
    }
  }

  @Test
  void initialRequestStillPerformsSaTokenAuthentication() throws Exception {
    MappedInterceptor interceptor = configuredInterceptor();

    try (var stp = mockStatic(StpUtil.class)) {
      assertThat(interceptor.getInterceptor()
                   .preHandle(request("/asset/files/1/2"), new MockHttpServletResponse(), new Object()))
        .isTrue();
      stp.verify(StpUtil::checkLogin, times(1));
    }
  }

  @Test
  void errorDispatchDoesNotRepeatSaTokenAuthentication() throws Exception {
    MappedInterceptor interceptor = configuredInterceptor();
    MockHttpServletRequest request = request("/error");
    request.setDispatcherType(DispatcherType.ERROR);

    try (var stp = mockStatic(StpUtil.class)) {
      assertThat(interceptor.getInterceptor()
                   .preHandle(request, new MockHttpServletResponse(), new Object()))
        .isTrue();
      stp.verifyNoInteractions();
    }
  }

  @Test
  void saTokenContextFilterIsExplicitlyRegisteredBeforeTenantFilter() {
    new ApplicationContextRunner()
      .withUserConfiguration(SaTokenContextRegister.class, SaTokenConfig.class)
      .withBean(TenantContextFilter.class, () -> mock(TenantContextFilter.class))
      .run(context -> {
        assertThat(context).hasBean("saTokenContextFilterRegistration");
        FilterRegistrationBean<?> registration =
          context.getBean("saTokenContextFilterRegistration", FilterRegistrationBean.class);
        assertThat(registration.getOrder()).isLessThan(Ordered.HIGHEST_PRECEDENCE + 10);
        assertThat(registration.determineDispatcherTypes())
          .contains(DispatcherType.REQUEST, DispatcherType.ASYNC, DispatcherType.ERROR);
      });
  }

  @Test
  void tenantContextFilterIsRegisteredForRequestAndAsyncDispatch() {
    FilterRegistrationBean<TenantContextFilter> registration =
      new SaTokenConfig().tenantContextFilterRegistration(mock(TenantContextFilter.class));

    assertThat(registration.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE + 10);
    assertThat(registration.determineDispatcherTypes())
      .containsExactlyInAnyOrder(DispatcherType.REQUEST, DispatcherType.ASYNC);
    assertThat(registration.isAsyncSupported()).isTrue();
  }

  private static MappedInterceptor configuredInterceptor() {
    InterceptorRegistry registry = new InterceptorRegistry();
    new SaTokenConfig().addInterceptors(registry);
    List<?> interceptors = ReflectionTestUtils.invokeMethod(registry, "getInterceptors");
    assertThat(interceptors).hasSize(1);
    return (MappedInterceptor) interceptors.getFirst();
  }

  private static MockHttpServletRequest request(String path) {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
    request.setServletPath(path);
    ServletRequestPathUtils.parseAndCache(request);
    return request;
  }
}

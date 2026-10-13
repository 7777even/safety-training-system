package com.sts.admin.tenant;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class TenantContextFilterTest {

  private final RegulatorViewResolver regulatorViewResolver = mock(RegulatorViewResolver.class);
  private final RegulatorAccessAuditor regulatorAccessAuditor = mock(RegulatorAccessAuditor.class);
  private final TenantContextFilter filter = new TenantContextFilter(
    regulatorViewResolver, regulatorAccessAuditor, new ObjectMapper());

  @AfterEach
  void clearContext() {
    TenantContext.clear();
  }

  @Test
  void superAdminRequest_bindsSelectedTenantInsteadOfBypass() throws Exception {
    SaSession session = mock(SaSession.class);
    when(session.get(TenantSessionKeys.SUPER_ADMIN)).thenReturn(true);
    when(session.get(TenantSessionKeys.ORGANIZATION_ID)).thenReturn(9L);
    when(session.getString(TenantSessionKeys.ORG_TYPE)).thenReturn("ENTERPRISE");
    AtomicReference<Long> seenTenant = new AtomicReference<>();
    AtomicBoolean seenBypass = new AtomicBoolean(true);

    try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
      stp.when(StpUtil::isLogin).thenReturn(true);
      stp.when(StpUtil::getTokenSession).thenReturn(session);

      filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(),
                      (request, response) -> {
                        seenTenant.set(TenantContext.requireTenantId());
                        seenBypass.set(TenantContext.isBypass());
                      });
    }

    assertThat(seenTenant).hasValue(9L);
    assertThat(seenBypass).isFalse();
    assertThat(TenantContext.isPresent()).isFalse();
  }

  @Test
  void legacySuperAdminSession_withoutTenant_failsClosed() throws Exception {
    SaSession session = mock(SaSession.class);
    when(session.get(TenantSessionKeys.SUPER_ADMIN)).thenReturn(true);
    AtomicBoolean chainCalled = new AtomicBoolean();
    MockHttpServletResponse response = new MockHttpServletResponse();

    try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
      stp.when(StpUtil::isLogin).thenReturn(true);
      stp.when(StpUtil::getTokenSession).thenReturn(session);
      stp.when(StpUtil::getLoginIdDefaultNull).thenReturn(1L);

      filter.doFilter(new MockHttpServletRequest(), response,
                      (request, servletResponse) -> chainCalled.set(true));
    }

    assertThat(chainCalled).isFalse();
    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(response.getContentAsString()).contains("重新登录");
    assertThat(TenantContext.isPresent()).isFalse();
  }

  @Test
  void superAdminRequest_rejectsRegulatorViewHeader() throws Exception {
    SaSession session = mock(SaSession.class);
    when(session.get(TenantSessionKeys.SUPER_ADMIN)).thenReturn(true);
    when(session.get(TenantSessionKeys.ORGANIZATION_ID)).thenReturn(9L);
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(RegulatorViewResolver.HEADER, "10");
    MockHttpServletResponse response = new MockHttpServletResponse();

    try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
      stp.when(StpUtil::isLogin).thenReturn(true);
      stp.when(StpUtil::getTokenSession).thenReturn(session);

      filter.doFilter(request, response, (req, res) -> {
        throw new AssertionError("非法切换租户时不得进入业务链");
      });
    }

    assertThat(response.getStatus()).isEqualTo(403);
  }

  @Test
  void asyncDispatch_rebindsSelectedTenantAndCleansUp() throws Exception {
    SaSession session = mock(SaSession.class);
    when(session.get(TenantSessionKeys.SUPER_ADMIN)).thenReturn(false);
    when(session.get(TenantSessionKeys.ORGANIZATION_ID)).thenReturn(9L);
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setDispatcherType(DispatcherType.ASYNC);
    AtomicReference<Long> seenTenant = new AtomicReference<>();

    try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
      stp.when(StpUtil::isLogin).thenReturn(true);
      stp.when(StpUtil::getTokenSession).thenReturn(session);

      filter.doFilter(request, new MockHttpServletResponse(),
                      (servletRequest, response) ->
                        seenTenant.set(TenantContext.requireTenantId()));
    }

    assertThat(seenTenant).hasValue(9L);
    assertThat(TenantContext.isPresent()).isFalse();
  }

  @Test
  void asyncSuperAdminDispatch_bindsSelectedTenantWithoutBypass() throws Exception {
    SaSession session = mock(SaSession.class);
    when(session.get(TenantSessionKeys.SUPER_ADMIN)).thenReturn(true);
    when(session.get(TenantSessionKeys.ORGANIZATION_ID)).thenReturn(10L);
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setDispatcherType(DispatcherType.ASYNC);
    AtomicReference<Long> seenTenant = new AtomicReference<>();
    AtomicBoolean seenBypass = new AtomicBoolean(true);

    try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
      stp.when(StpUtil::isLogin).thenReturn(true);
      stp.when(StpUtil::getTokenSession).thenReturn(session);

      filter.doFilter(request, new MockHttpServletResponse(),
                      (servletRequest, response) -> {
                        seenTenant.set(TenantContext.requireTenantId());
                        seenBypass.set(TenantContext.isBypass());
                      });
    }

    assertThat(seenTenant).hasValue(10L);
    assertThat(seenBypass).isFalse();
    assertThat(TenantContext.isPresent()).isFalse();
  }

  @Test
  void asyncDispatch_clearsTenantWhenDownstreamFails() {
    SaSession session = mock(SaSession.class);
    when(session.get(TenantSessionKeys.SUPER_ADMIN)).thenReturn(false);
    when(session.get(TenantSessionKeys.ORGANIZATION_ID)).thenReturn(11L);
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setDispatcherType(DispatcherType.ASYNC);

    try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
      stp.when(StpUtil::isLogin).thenReturn(true);
      stp.when(StpUtil::getTokenSession).thenReturn(session);

      assertThatThrownBy(() -> filter.doFilter(
        request, new MockHttpServletResponse(),
        (servletRequest, response) -> {
          assertThat(TenantContext.requireTenantId()).isEqualTo(11L);
          throw new ServletException("downstream failure");
        }))
        .isInstanceOf(ServletException.class)
        .hasMessage("downstream failure");
    }

    assertThat(TenantContext.isPresent()).isFalse();
  }

  @Test
  void asyncDispatch_withoutTenant_failsClosed() throws Exception {
    SaSession session = mock(SaSession.class);
    when(session.get(TenantSessionKeys.SUPER_ADMIN)).thenReturn(true);
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setDispatcherType(DispatcherType.ASYNC);
    MockHttpServletResponse response = new MockHttpServletResponse();
    AtomicBoolean chainCalled = new AtomicBoolean();

    try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
      stp.when(StpUtil::isLogin).thenReturn(true);
      stp.when(StpUtil::getTokenSession).thenReturn(session);
      stp.when(StpUtil::getLoginIdDefaultNull).thenReturn(1L);

      filter.doFilter(request, response,
                      (servletRequest, servletResponse) -> chainCalled.set(true));
    }

    assertThat(chainCalled).isFalse();
    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(response.getContentAsString()).contains("重新登录");
    assertThat(TenantContext.isPresent()).isFalse();
  }
}

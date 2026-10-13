package com.sts.archive.service;

import cn.dev33.satoken.stp.StpUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

/**
 * 档案证书清单的权限门控。
 * <p>
 * 档案权限（{@code archive:training:list}）不等于证书读取权限，缺少证书权限时必须整字段不返回。
 */
class ArchiveCertificateGuardTest {

  private final ArchiveCertificateGuard guard = new ArchiveCertificateGuard();

  @Test
  @DisplayName("具备证书查询权限时放行证书清单")
  void hasPermission_isAllowed() {
    try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
      stp.when(StpUtil::isLogin).thenReturn(true);
      stp.when(() -> StpUtil.hasPermission(ArchiveCertificateGuard.CERTIFICATE_LIST_PERMISSION)).thenReturn(true);

      assertThat(guard.canListCertificates()).isTrue();
    }
  }

  @Test
  @DisplayName("仅具备档案权限时不放行证书清单")
  void archivePermissionOnly_isDenied() {
    try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
      stp.when(StpUtil::isLogin).thenReturn(true);
      stp.when(() -> StpUtil.hasPermission(ArchiveCertificateGuard.CERTIFICATE_LIST_PERMISSION)).thenReturn(false);

      assertThat(guard.canListCertificates()).isFalse();
    }
  }

  @Test
  @DisplayName("未登录按无权限处理，不触发权限查询异常")
  void notLogin_isDenied() {
    try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
      stp.when(StpUtil::isLogin).thenReturn(false);

      assertThat(guard.canListCertificates()).isFalse();
      stp.verify(() -> StpUtil.hasPermission(ArchiveCertificateGuard.CERTIFICATE_LIST_PERMISSION),
                 org.mockito.Mockito.never());
    }
  }
}

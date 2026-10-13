package com.sts.admin.config;

import com.sts.admin.entity.SysUser;
import com.sts.admin.system.service.SysUserCacheService;
import com.sts.admin.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StpInterfaceImplTest {

  private final SysUserCacheService userCacheService = mock(SysUserCacheService.class);
  private final StpInterfaceImpl stpInterface = new StpInterfaceImpl(userCacheService);

  @AfterEach
  void clearTenant() {
    TenantContext.clear();
  }

  @Test
  void superAdminPermissionLookup_readsPlatformIdentityAndRestoresSelectedTenant() {
    SysUser superAdmin = new SysUser();
    superAdmin.setUserId(1L);
    superAdmin.setOrganizationId(1L);
    superAdmin.setUserCode("superAdmin");
    when(userCacheService.getById(1L)).thenAnswer(invocation -> {
      assertThat(TenantContext.isBypass()).isTrue();
      return superAdmin;
    });
    TenantContext.setTenantId(9L);

    assertThat(stpInterface.getPermissionList(1L, "login")).isEqualTo(List.of("*"));
    assertThat(TenantContext.requireTenantId()).isEqualTo(9L);
  }
}

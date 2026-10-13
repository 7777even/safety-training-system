package com.sts.admin.storage;

import com.sts.admin.tenant.TenantContext;
import com.sts.common.exception.BusinessException;
import com.sts.org.entity.Organization;
import com.sts.org.mapper.OrganizationMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TenantObjectKeyServiceTest {

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void prefixesExistingObjectKeyWithImmutableCurrentOrganizationCode() {
    TenantContext.setTenantId(7L);
    OrganizationMapper mapper = mock(OrganizationMapper.class);
    Organization organization = new Organization();
    organization.setOrganizationId(7L);
    organization.setOrgCode("ACME_01");
    when(mapper.selectById(7L)).thenReturn(organization);
    TenantObjectKeyService service = new TenantObjectKeyService(mapper);

    assertThat(service.forCurrentTenant("source/2026/08/id.pdf"))
      .isEqualTo("ACME_01/source/2026/08/id.pdf");
    assertThat(service.forCurrentTenant("derivative/20/9/hls/index.m3u8"))
      .isEqualTo("ACME_01/derivative/20/9/hls/index.m3u8");
  }

  @Test
  void differentOrganizationsUseDistinctRootDirectories() {
    OrganizationMapper mapper = mock(OrganizationMapper.class);
    Organization acme = new Organization();
    acme.setOrgCode("ACME");
    Organization beta = new Organization();
    beta.setOrgCode("BETA");
    when(mapper.selectById(7L)).thenReturn(acme);
    when(mapper.selectById(8L)).thenReturn(beta);
    TenantObjectKeyService service = new TenantObjectKeyService(mapper);

    TenantContext.setTenantId(7L);
    String acmeKey = service.forCurrentTenant("source/2026/08/id.pdf");
    TenantContext.setTenantId(8L);
    String betaKey = service.forCurrentTenant("source/2026/08/id.pdf");

    assertThat(acmeKey).isEqualTo("ACME/source/2026/08/id.pdf");
    assertThat(betaKey).isEqualTo("BETA/source/2026/08/id.pdf");
  }

  @Test
  void rejectsMissingOrganizationOrUnsafeRelativeKeyWithoutFallback() {
    TenantContext.setTenantId(7L);
    OrganizationMapper mapper = mock(OrganizationMapper.class);
    TenantObjectKeyService service = new TenantObjectKeyService(mapper);

    assertThatThrownBy(() -> service.forCurrentTenant("source/id.pdf"))
      .isInstanceOf(BusinessException.class)
      .hasFieldOrPropertyWithValue("code", 4051);

    Organization organization = new Organization();
    organization.setOrgCode("ACME");
    when(mapper.selectById(7L)).thenReturn(organization);
    assertThatThrownBy(() -> service.forCurrentTenant("../source/id.pdf"))
      .isInstanceOf(BusinessException.class)
      .hasFieldOrPropertyWithValue("code", 4051);
  }

  @Test
  void acceptsOnlyLegacyOrCurrentTenantCourseCoverKeys() {
    TenantContext.setTenantId(7L);
    OrganizationMapper mapper = mock(OrganizationMapper.class);
    Organization organization = new Organization();
    organization.setOrgCode("ACME");
    when(mapper.selectById(7L)).thenReturn(organization);
    TenantObjectKeyService service = new TenantObjectKeyService(mapper);

    assertThat(service.isLegacyOrCurrentTenantKey("course-cover/2026/08/id.png", "course-cover/"))
      .isTrue();
    assertThat(service.isLegacyOrCurrentTenantKey("ACME/course-cover/2026/08/id.png", "course-cover/"))
      .isTrue();
    assertThat(service.isLegacyOrCurrentTenantKey("OTHER/course-cover/2026/08/id.png", "course-cover/"))
      .isFalse();
  }
}

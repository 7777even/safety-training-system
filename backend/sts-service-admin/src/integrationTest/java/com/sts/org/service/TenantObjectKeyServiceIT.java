package com.sts.org.service;

import com.sts.admin.storage.TenantObjectKeyService;
import com.sts.admin.support.AbstractMysqlIntegrationTest;
import com.sts.admin.tenant.TenantContext;
import com.sts.org.entity.Organization;
import com.sts.org.mapper.OrganizationMapper;
import com.sts.org.support.OrgMapperItConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = OrgMapperItConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class TenantObjectKeyServiceIT extends AbstractMysqlIntegrationTest {

  @Autowired
  private OrganizationMapper organizationMapper;

  @Test
  void resolvesImmutableOrganizationCodeFromRealTenantMasterData() {
    TenantContext.setTenantId(demoTenantId());
    Organization organization = organizationMapper.selectById(demoTenantId());
    TenantObjectKeyService service = new TenantObjectKeyService(organizationMapper);

    assertThat(organization).isNotNull();
    assertThat(organization.getOrgCode()).isEqualTo(DEMO_ORG_CODE);
    assertThat(service.forCurrentTenant("source/2026/08/id.pdf"))
      .isEqualTo(DEMO_ORG_CODE + "/source/2026/08/id.pdf");
  }
}

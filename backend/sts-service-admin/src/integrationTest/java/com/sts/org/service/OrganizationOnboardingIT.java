package com.sts.org.service;

import com.sts.admin.auth.strategy.AuthenticationResult;
import com.sts.admin.auth.strategy.LocalLoginStrategy;
import com.sts.admin.support.AbstractMysqlIntegrationTest;
import com.sts.admin.tenant.TenantContext;
import com.sts.org.dto.OrganizationCreateRequest;
import com.sts.org.support.OrganizationOnboardingItConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = OrganizationOnboardingItConfig.class,
  webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class OrganizationOnboardingIT extends AbstractMysqlIntegrationTest {

  private static final String ADMIN_PASSWORD_HASH =
    "b645e32cb9efb021dcfb4a0fe0fce2c318cdf4478a5c908d111a830b58dab39e";

  @Autowired
  private OrganizationService organizationService;

  @Autowired
  private LocalLoginStrategy localLoginStrategy;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Test
  void createEnterprise_createsOnlyLoginReadyAdminWithoutDepartment() {
    Long superAdminId = insertSuperAdmin();
    insertVisibleMenu("system:tenant:list");
    insertVisibleMenu("sys:user:list");
    Long originalTenant = TenantContext.requireTenantId();

    OrganizationCreateRequest request = new OrganizationCreateRequest();
    request.setOrgCode("ONBOARD-IT");
    request.setOrgName("开户集成测试企业");
    request.setOrgType("ENTERPRISE");
    request.setAdminUserCode("admin");
    request.setAdminUserName("企业管理员");
    request.setAdminPassword(ADMIN_PASSWORD_HASH);

    Long organizationId = organizationService.createOrganization(superAdminId, request);

    assertThat(TenantContext.requireTenantId()).isEqualTo(originalTenant);
    assertThat(jdbcTemplate.queryForObject(
      "SELECT COUNT(*) FROM us_sys_dept WHERE organization_id = ? AND deleted = 0",
      Long.class, organizationId)).isZero();
    assertThat(jdbcTemplate.queryForObject(
      "SELECT COUNT(*) FROM us_sys_user WHERE organization_id = ? AND deleted = 0",
      Long.class, organizationId)).isEqualTo(1L);
    assertThat(jdbcTemplate.queryForObject(
      "SELECT dept_id FROM us_sys_user WHERE organization_id = ? AND user_code = 'admin'",
      Long.class, organizationId)).isNull();
    Long adminRoleId = jdbcTemplate.queryForObject(
      "SELECT role_id FROM us_sys_role WHERE organization_id = ? AND role_code = 'admin'",
      Long.class, organizationId);
    assertThat(jdbcTemplate.queryForObject(
      "SELECT COUNT(*) FROM us_sys_role_menu WHERE role_id = ?",
      Long.class, adminRoleId)).isEqualTo(2L);

    AuthenticationResult authentication = localLoginStrategy.authenticate(Map.of(
      "orgCode", "ONBOARD-IT",
      "userCode", "admin",
      "password", ADMIN_PASSWORD_HASH));
    assertThat(authentication.organizationId()).isEqualTo(organizationId);
  }

  private Long insertSuperAdmin() {
    jdbcTemplate.update(
      "INSERT INTO us_sys_user (organization_id, user_code, user_name, member_type, status, dept_id)"
        + " VALUES (?, 'superAdmin', '平台管理员', 'INTERNAL', 1, NULL)",
      demoTenantId());
    return jdbcTemplate.queryForObject(
      "SELECT user_id FROM us_sys_user WHERE user_code = 'superAdmin'", Long.class);
  }

  private void insertVisibleMenu(String permission) {
    jdbcTemplate.update(
      "INSERT INTO us_sys_menu (parent_id, menu_name, perms, is_visible, menu_type)"
        + " VALUES (0, ?, ?, 1, 'F')",
      permission, permission);
  }
}

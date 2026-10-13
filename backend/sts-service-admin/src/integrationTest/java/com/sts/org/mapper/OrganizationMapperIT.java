package com.sts.org.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sts.admin.support.AbstractMysqlIntegrationTest;
import com.sts.org.entity.Organization;
import com.sts.org.mapper.OrganizationMapper;
import com.sts.org.support.OrgMapperItConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 租户主数据持久化 IT。
 * <p>
 * 验证扁平租户模型：两类租户、编码唯一、无层级字段。
 */
@SpringBootTest(classes = OrgMapperItConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class OrganizationMapperIT extends AbstractMysqlIntegrationTest {

  @Autowired
  private OrganizationMapper organizationMapper;

  @Test
  void insert_mapsFieldsAndFillsAuditColumns() {
    Organization organization = organization("ACME-IT-01", "集成测试企业", "ENTERPRISE");
    organizationMapper.insert(organization);

    Organization saved = organizationMapper.selectById(organization.getOrganizationId());
    assertThat(saved.getOrganizationId()).isNotNull();
    assertThat(saved.getOrgCode()).isEqualTo("ACME-IT-01");
    assertThat(saved.getOrgType()).isEqualTo("ENTERPRISE");
    assertThat(saved.getCreateTime()).isNotNull();
    assertThat(saved.getVersion()).isZero();
  }

  @Test
  void uniqueKey_rejectsDuplicateOrgCode() {
    organizationMapper.insert(organization("ACME-IT-02", "企业一", "ENTERPRISE"));

    assertThatThrownBy(() -> organizationMapper.insert(organization("ACME-IT-02", "企业二", "ENTERPRISE")))
      .isInstanceOf(DuplicateKeyException.class);
  }

  @Test
  void seed_containsExactlyOneRegulatorTenant() {
    Long regulators = organizationMapper.selectCount(
      new LambdaQueryWrapper<Organization>().eq(Organization::getOrgType, "REGULATOR"));

    assertThat(regulators).isEqualTo(1L);
    assertThat(organizationMapper.selectByOrgCode("REGULATOR").getOrgName()).isEqualTo("监管平台");
  }

  @Test
  void selectByOrgCode_resolvesTenantForLoginEntry() {
    Organization demo = organizationMapper.selectByOrgCode("DEMO");

    assertThat(demo).isNotNull();
    assertThat(demo.getOrgType()).isEqualTo("ENTERPRISE");
    assertThat(demo.getStatus()).isEqualTo(1);
  }

  @Test
  void selectPage_filtersTypeAndUsesRealPagination() {
    organizationMapper.insert(organization("ACME-IT-03", "分页样本甲", "ENTERPRISE"));
    organizationMapper.insert(organization("ACME-IT-04", "分页样本乙", "ENTERPRISE"));

    Page<Organization> page = organizationMapper.selectPage(
      new Page<>(1, 1),
      new LambdaQueryWrapper<Organization>()
        .eq(Organization::getOrgType, "ENTERPRISE")
        .like(Organization::getOrgName, "分页样本")
        .orderByAsc(Organization::getOrgCode));

    assertThat(page.getRecords()).hasSize(1);
    assertThat(page.getTotal()).isEqualTo(2);
    assertThat(page.getRecords().getFirst().getOrgCode()).isEqualTo("ACME-IT-03");
  }

  private static Organization organization(String code, String name, String type) {
    Organization organization = new Organization();
    organization.setOrgCode(code);
    organization.setOrgName(name);
    organization.setOrgType(type);
    organization.setStatus(1);
    organization.setDescription("");
    return organization;
  }
}

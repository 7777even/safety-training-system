package com.sts.admin.tenant;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sts.admin.entity.SysUser;
import com.sts.admin.mapper.SysUserMapper;
import com.sts.admin.support.AbstractMysqlIntegrationTest;
import com.sts.admin.support.MapperItConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 租户内账号唯一性验证（Testcontainers 真 MySQL）。
 * <p>
 * 工号由全局唯一改为租户内唯一，这条只有真库的唯一索引能证明。
 */
@SpringBootTest(classes = MapperItConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class TenantAccountIT extends AbstractMysqlIntegrationTest {

  @Autowired
  private SysUserMapper userMapper;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  private Long otherTenantId() {
    jdbcTemplate.update(
      "INSERT INTO sts_org_organization (org_code, org_name, org_type, status, description)"
        + " VALUES ('ACC-OTHER', '另一家企业', 'ENTERPRISE', 1, '')");
    return jdbcTemplate.queryForObject(
      "SELECT organization_id FROM sts_org_organization WHERE org_code = 'ACC-OTHER'", Long.class);
  }

  private SysUser user(String userCode) {
    SysUser user = new SysUser();
    user.setUserCode(userCode);
    user.setUserName("账号-" + userCode);
    user.setMemberType("INTERNAL");
    user.setStatus(1);
    user.setDeptId(1L);
    return user;
  }

  @Test
  @DisplayName("同一工号可在不同租户各存在一个")
  void sameUserCode_isAllowedAcrossTenants() {
    userMapper.insert(user("admin"));
    Long other = otherTenantId();

    TenantContext.callWithTenant(other, () -> userMapper.insert(user("admin")));

    Long total = jdbcTemplate.queryForObject(
      "SELECT COUNT(*) FROM us_sys_user WHERE user_code = 'admin' AND deleted = 0", Long.class);
    assertThat(total).describedAs("两个租户各有一个 admin").isEqualTo(2L);
  }

  @Test
  @DisplayName("同租户内工号重复触发唯一键冲突")
  void duplicateUserCode_inSameTenant_isRejected() {
    userMapper.insert(user("dup-user"));

    assertThatThrownBy(() -> userMapper.insert(user("dup-user")))
      .isInstanceOf(DuplicateKeyException.class);
  }

  @Test
  @DisplayName("按工号查询只命中本租户账号")
  void selectByUserCode_isTenantScoped() {
    userMapper.insert(user("scoped"));
    Long other = otherTenantId();
    TenantContext.callWithTenant(other, () -> userMapper.insert(user("scoped")));

    Long demoTenant = demoTenantId();
    SysUser mine = userMapper.selectOne(
      new LambdaQueryWrapper<SysUser>().eq(SysUser::getUserCode, "scoped"));

    assertThat(mine).isNotNull();
    assertThat(mine.getOrganizationId()).isEqualTo(demoTenant);
  }

  @Test
  @DisplayName("用户档案字段随用户落库，不再依赖机构成员表")
  void profileFields_persistOnUser() {
    SysUser user = user("profile");
    user.setJobTitle("安全员");
    user.setEmail("safety@example.com");
    user.setMemberType("EXTERNAL");
    userMapper.insert(user);

    SysUser saved = userMapper.selectById(user.getUserId());
    assertThat(saved.getJobTitle()).isEqualTo("安全员");
    assertThat(saved.getEmail()).isEqualTo("safety@example.com");
    assertThat(saved.getMemberType()).isEqualTo("EXTERNAL");
  }

  @Test
  @DisplayName("机构成员表已删除")
  void orgMemberTable_isGone() {
    Long exists = jdbcTemplate.queryForObject(
      "SELECT COUNT(*) FROM information_schema.tables"
        + " WHERE table_schema = DATABASE() AND table_name = 'sts_org_member'", Long.class);

    assertThat(exists).isZero();
  }
}

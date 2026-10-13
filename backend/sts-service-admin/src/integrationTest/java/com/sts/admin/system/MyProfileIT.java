package com.sts.admin.system;

import cn.dev33.satoken.stp.StpUtil;
import com.sts.admin.support.AbstractMysqlIntegrationTest;
import com.sts.admin.system.dto.*;
import com.sts.admin.system.service.MyProfileService;
import com.sts.admin.tenant.TenantContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 真实MySQL验证白名单更新、空值清除、乐观锁及凭证事务。 */
@org.springframework.boot.test.context.SpringBootTest(classes = MyProfileIT.Config.class)
class MyProfileIT extends AbstractMysqlIntegrationTest {
  @org.springframework.context.annotation.Configuration
  @org.springframework.context.annotation.Import(com.sts.admin.support.MapperItConfig.class)
  @org.springframework.transaction.annotation.EnableTransactionManagement
  @org.mybatis.spring.annotation.MapperScan({"com.sts.admin.mapper", "com.sts.org.mapper"})
  static class Config {
    @org.springframework.context.annotation.Bean PasswordEncoder passwords() { return new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(); }
    @org.springframework.context.annotation.Bean TransactionTemplate transaction(javax.sql.DataSource source) {
      return new TransactionTemplate(new org.springframework.jdbc.datasource.DataSourceTransactionManager(source));
    }
    @org.springframework.context.annotation.Bean MyProfileService profiles(com.sts.admin.mapper.SysUserMapper users,
      com.sts.admin.mapper.SysAuthIdentityMapper identities, com.sts.admin.mapper.SysDeptMapper departments,
      com.sts.org.mapper.OrganizationMapper organizations, com.sts.admin.mapper.SysRoleMapper roles, PasswordEncoder passwords) {
      return new MyProfileService(users, identities, departments, organizations, roles,
        mock(com.sts.admin.system.service.SysUserCacheService.class), mock(com.sts.admin.cache.CacheEvictService.class), passwords);
    }
  }
  @Autowired private MyProfileService profiles;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private PasswordEncoder passwords;
  @Autowired private TransactionTemplate transaction;

  @Test void profileAndCredentialWritesRespectConflictRollbackAndOwner() {
    long id = 98768001L; Long org = demoTenantId();
    jdbc.update("INSERT INTO us_sys_user(user_id,organization_id,user_code,user_name,phone,email,member_type,status) VALUES(?,?, 'B4-PROFILE-IT','原姓名','13800000007','old@example.test','INTERNAL',1)", id, org);
    jdbc.update("INSERT INTO us_sys_auth_identity(user_id,identity_type,identifier,credential) VALUES(?,'local','DEMO:B4-PROFILE-IT',?)", id, passwords.encode("a".repeat(64)));
    try (var auth = mockStatic(StpUtil.class)) {
      auth.when(StpUtil::getLoginIdAsLong).thenReturn(id);
      TenantContext.runWithTenant(org, () -> {
        assertThat(profiles.read().phone()).isEqualTo("13800000007");
        var update = new MyProfileRequest(); update.setUserName("新姓名"); update.setVersion(0);
        profiles.update(update);
        assertThat(profiles.read().userName()).isEqualTo("新姓名");
        assertThat(profiles.read().phone()).isNull(); assertThat(profiles.read().email()).isNull();
        assertThat(profiles.read().version()).isEqualTo(1);
        assertThatThrownBy(() -> profiles.update(update)).hasMessageContaining("修改");
        assertThat(jdbc.queryForObject("SELECT user_code FROM us_sys_user WHERE user_id=?", String.class, id)).isEqualTo("B4-PROFILE-IT");
        var password = new MyPasswordRequest(); password.setOldPassword("a".repeat(64)); password.setNewPassword("b".repeat(64));
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
          profiles.changePassword(password); throw new IllegalStateException("模拟回滚");
        })).hasMessageContaining("回滚");
        auth.verify(() -> StpUtil.logout(id), never());
        assertThat(passwords.matches("a".repeat(64), jdbc.queryForObject("SELECT credential FROM us_sys_auth_identity WHERE user_id=?", String.class, id))).isTrue();
        profiles.changePassword(password); auth.verify(() -> StpUtil.logout(id));
        assertThat(passwords.matches("b".repeat(64), jdbc.queryForObject("SELECT credential FROM us_sys_auth_identity WHERE user_id=?", String.class, id))).isTrue();
        assertThatThrownBy(() -> profiles.changePassword(password)).hasMessageContaining("旧密码");
      });
      TenantContext.runWithTenant(org + 1000, () -> assertThatThrownBy(profiles::read).hasMessageContaining("不存在"));
    }
  }
}

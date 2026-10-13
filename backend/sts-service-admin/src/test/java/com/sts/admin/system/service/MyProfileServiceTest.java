package com.sts.admin.system.service;

import cn.dev33.satoken.stp.StpUtil;
import com.sts.admin.cache.CacheEvictService;
import com.sts.admin.entity.*;
import com.sts.admin.mapper.*;
import com.sts.admin.system.dto.MyPasswordRequest;
import com.sts.admin.system.dto.MyProfileRequest;
import com.sts.admin.tenant.TenantContext;
import com.sts.org.mapper.OrganizationMapper;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 本人资料和改密通过公开服务验证，不依赖管理编辑权限。 */
class MyProfileServiceTest {
  @org.junit.jupiter.api.BeforeAll static void registerUserTable() {
    com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
      new org.apache.ibatis.builder.MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(), "profile-test"), SysUser.class);
  }
  @Test void ownProfileUsesRealPhoneAndOnlyUpdatesAllowedFields() {
    var users = mock(SysUserMapper.class); var identities = mock(SysAuthIdentityMapper.class);
    var user = new SysUser(); user.setUserId(7L); user.setOrganizationId(2L);
    user.setUserCode("learner"); user.setUserName("原姓名"); user.setPhone("13800000007"); user.setVersion(3);
    when(users.selectById(7L)).thenReturn(user);
    var service = new MyProfileService(users, identities, mock(SysDeptMapper.class), mock(OrganizationMapper.class),
      mock(SysRoleMapper.class), mock(SysUserCacheService.class), mock(CacheEvictService.class), new BCryptPasswordEncoder());
    try (var auth = mockStatic(StpUtil.class)) {
      auth.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
      TenantContext.runWithTenant(2L, () -> {
        assertThat(service.read().phone()).isEqualTo("13800000007");
        var request = new MyProfileRequest(); request.setUserName("新姓名"); request.setPhone(null); request.setEmail(null); request.setVersion(3);
        service.update(request);
        var captor = org.mockito.ArgumentCaptor.forClass(SysUser.class);
        verify(users).updateWithVersionCheck(captor.capture(), any());
        assertThat(captor.getValue().getUserId()).isEqualTo(7L);
        assertThat(captor.getValue().getDeptId()).isNull();
        assertThat(captor.getValue().getUserCode()).isNull();
      });
    }
  }
  @Test void wrongOldPasswordDoesNotWriteAndSuccessfulChangeRevokesSessions() {
    var users = mock(SysUserMapper.class); var identities = mock(SysAuthIdentityMapper.class);
    var encoder = new BCryptPasswordEncoder();
    var user = new SysUser(); user.setUserId(7L); user.setOrganizationId(2L); user.setUserCode("learner");
    var identity = new SysAuthIdentity(); identity.setUserId(7L); identity.setIdentityId(9L); identity.setVersion(0); identity.setCredential(encoder.encode("a".repeat(64)));
    when(users.selectById(7L)).thenReturn(user); when(identities.selectOne(any())).thenReturn(identity);
    var service = new MyProfileService(users, identities, mock(SysDeptMapper.class), mock(OrganizationMapper.class),
      mock(SysRoleMapper.class), mock(SysUserCacheService.class), mock(CacheEvictService.class), encoder);
    try (var auth = mockStatic(StpUtil.class)) {
      auth.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
      TenantContext.runWithTenant(2L, () -> {
        var request = new MyPasswordRequest(); request.setOldPassword("c".repeat(64)); request.setNewPassword("b".repeat(64));
        assertThatThrownBy(() -> service.changePassword(request)).hasMessageContaining("旧密码");
        verify(identities, never()).updateByIdWithVersionCheck(any());
        request.setOldPassword("a".repeat(64)); service.changePassword(request);
        assertThat(encoder.matches("b".repeat(64), identity.getCredential())).isTrue();
        auth.verify(() -> StpUtil.logout(7L));
      });
    }
  }
  @Test void passwordDtoIsWriteOnlyAndCannotReceiveAuthorizationFields() throws Exception {
    var json = new com.fasterxml.jackson.databind.ObjectMapper();
    json.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    json.configure(com.fasterxml.jackson.databind.SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
    var profile = json.readValue("{\"userName\":\"本人\",\"version\":1,\"roleIds\":[1],\"organizationId\":999}", MyProfileRequest.class);
    assertThat(profile.getUserName()).isEqualTo("本人");
    assertThat(json.writeValueAsString(profile)).doesNotContain("roleIds", "organizationId");
    var password = new MyPasswordRequest(); password.setOldPassword("a".repeat(64)); password.setNewPassword("b".repeat(64));
    assertThat(json.writeValueAsString(password)).doesNotContain("aaaa", "bbbb", "oldPassword", "newPassword");
    assertThat(password.toString()).doesNotContain("aaaa", "bbbb");
  }
}

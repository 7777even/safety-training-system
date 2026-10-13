package com.sts.admin.auth.strategy;

import com.sts.admin.entity.SysAuthIdentity;
import com.sts.admin.entity.SysUser;
import com.sts.admin.mapper.SysAuthIdentityMapper;
import com.sts.admin.mapper.SysUserMapper;
import com.sts.common.exception.BusinessException;
import com.sts.org.entity.Organization;
import com.sts.org.mapper.OrganizationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * {@link LocalLoginStrategy} 单元测试（登录安全核心）。
 * <p>
 * 纯 Mockito 单测：mock Mapper + 真实 {@link BCryptPasswordEncoder}（strength=10，与种子灌注一致），
 * 不加载 Spring 上下文、不碰 DB / Sa-Token。
 * <p>
 * 多租户登录契约：企业编码 + 租户内工号 + 密码；凭证标识为 {@code {orgCode}:{userCode}} 复合值。
 * 全部失败分支使用同一话术，避免枚举租户或账号。
 */
class LocalLoginStrategyTest {

  private static final String ORG_CODE = "ACME";
  private static final String USER_CODE = "admin";
  private static final String IDENTIFIER = ORG_CODE + ":" + USER_CODE;
    private static final String PLAINTEXT = "admin123";
  private static final String FAIL_MESSAGE = "企业编码或账号密码错误";

    private SysUserMapper userMapper;
    private SysAuthIdentityMapper authIdentityMapper;
  private OrganizationMapper organizationMapper;
    private PasswordEncoder passwordEncoder;
    private LocalLoginStrategy strategy;

    private String frontHash;         // 前端 SHA-256(明文)
    private String storedCredential;  // 库中 BCrypt(frontHash)

    @BeforeEach
    void setUp() {
        userMapper = mock(SysUserMapper.class);
        authIdentityMapper = mock(SysAuthIdentityMapper.class);
      organizationMapper = mock(OrganizationMapper.class);
        passwordEncoder = new BCryptPasswordEncoder(10);
      strategy = new LocalLoginStrategy(userMapper, authIdentityMapper, organizationMapper, passwordEncoder);

        frontHash = sha256(PLAINTEXT);
        storedCredential = passwordEncoder.encode(frontHash);
      when(organizationMapper.selectByOrgCode(ORG_CODE)).thenReturn(enabledTenant(5L));
    }

    @Test
    @DisplayName("企业编码 + 工号 + 密码正确 → 返回用户与登录租户")
    void authenticate_success_returnsUserAndLoginTenant() {
      when(authIdentityMapper.selectOne(any())).thenReturn(identityWith(storedCredential));
      when(userMapper.selectById(2L)).thenReturn(enabledUser(2L, 5L));

      AuthenticationResult result = strategy.authenticate(params(ORG_CODE, USER_CODE, frontHash));

      assertThat(result.userId()).isEqualTo(2L);
      assertThat(result.organizationId()).isEqualTo(5L);
      assertThat(result.orgType()).isEqualTo("ENTERPRISE");
    }

  @Test
  @DisplayName("凭证标识由企业编码与工号拼成，保证租户内唯一")
  void authenticate_looksUpCompositeIdentifier() {
        when(authIdentityMapper.selectOne(any())).thenReturn(identityWith(storedCredential));
    when(userMapper.selectById(2L)).thenReturn(enabledUser(2L, 5L));

    strategy.authenticate(params(" acme ", " admin ", frontHash));

    // 企业编码统一大写、两端去空格后再拼接
    verify(organizationMapper).selectByOrgCode(ORG_CODE);
    assertThat(strategy.buildIdentifier(ORG_CODE, USER_CODE)).isEqualTo(IDENTIFIER);
    }

    @Test
    @DisplayName("企业编码不存在 → 401 统一话术，且不查凭证（不泄露租户是否存在）")
    void authenticate_tenantNotFound_unauthorized() {
      when(organizationMapper.selectByOrgCode("GHOST")).thenReturn(null);

      assertBusinessError(() -> strategy.authenticate(params("GHOST", USER_CODE, frontHash)),
                          HttpStatus.UNAUTHORIZED, 1002, FAIL_MESSAGE);

        verify(authIdentityMapper, never()).selectOne(any());
    }

    @Test
    @DisplayName("租户已停用 → 401 统一话术，且不查凭证")
    void authenticate_tenantDisabled_unauthorized() {
      Organization disabled = enabledTenant(5L);
      disabled.setStatus(0);
      when(organizationMapper.selectByOrgCode(ORG_CODE)).thenReturn(disabled);

      assertBusinessError(() -> strategy.authenticate(params(ORG_CODE, USER_CODE, frontHash)),
                          HttpStatus.UNAUTHORIZED, 1002, FAIL_MESSAGE);

      verify(authIdentityMapper, never()).selectOne(any());
    }

  @Test
  @DisplayName("凭证不存在 → 401 统一话术，与企业编码不存在无法区分")
  void authenticate_identityNotFound_unauthorized() {
    when(authIdentityMapper.selectOne(any())).thenReturn(null);

    assertBusinessError(() -> strategy.authenticate(params(ORG_CODE, USER_CODE, frontHash)),
                        HttpStatus.UNAUTHORIZED, 1002, FAIL_MESSAGE);
  }

  @Test
  @DisplayName("密码不匹配 → 401 统一话术")
  void authenticate_wrongPassword_unauthorized() {
    when(authIdentityMapper.selectOne(any())).thenReturn(identityWith(storedCredential));
    when(userMapper.selectById(2L)).thenReturn(enabledUser(2L, 5L));

    assertBusinessError(() -> strategy.authenticate(params(ORG_CODE, USER_CODE, sha256("wrong"))),
                        HttpStatus.UNAUTHORIZED, 1002, FAIL_MESSAGE);
  }

  @Test
  @DisplayName("账号被禁用 → 403/1003")
    void authenticate_disabledUser_forbidden() {
    when(authIdentityMapper.selectOne(any())).thenReturn(identityWith(storedCredential));
    SysUser disabled = enabledUser(2L, 5L);
        disabled.setStatus(0);
    when(userMapper.selectById(2L)).thenReturn(disabled);

    assertBusinessError(() -> strategy.authenticate(params(ORG_CODE, USER_CODE, frontHash)),
                            HttpStatus.FORBIDDEN, 1003, "您的账号已被禁用");
    }

    @Test
    @DisplayName("账号租户与企业编码不一致 → 401 统一话术，阻断跨租户登录")
    void authenticate_userBelongsToAnotherTenant_unauthorized() {
        when(authIdentityMapper.selectOne(any())).thenReturn(identityWith(storedCredential));
      when(userMapper.selectById(2L)).thenReturn(enabledUser(2L, 99L));

      assertBusinessError(() -> strategy.authenticate(params(ORG_CODE, USER_CODE, frontHash)),
                          HttpStatus.UNAUTHORIZED, 1002, FAIL_MESSAGE);
    }

  @Test
  @DisplayName("普通凭证异常关联到 superAdmin 主体 → 401 失败关闭")
  void authenticate_normalIdentityPointsToSuperAdmin_unauthorized() {
    when(authIdentityMapper.selectOne(any())).thenReturn(identityWith(storedCredential));
    SysUser superAdmin = enabledUser(2L, 5L);
    superAdmin.setUserCode("superAdmin");
    when(userMapper.selectById(2L)).thenReturn(superAdmin);

    assertBusinessError(() -> strategy.authenticate(params(ORG_CODE, USER_CODE, frontHash)),
                        HttpStatus.UNAUTHORIZED, 1002, FAIL_MESSAGE);
  }

  @Test
  @DisplayName("superAdmin 使用平台凭证登录其它企业 → 成功且登录租户为目标企业")
  void authenticate_superAdminUsesPlatformCredentialForTargetTenant() {
    SysUser superAdmin = enabledUser(1L, 1L);
    superAdmin.setUserCode("superAdmin");
    SysAuthIdentity identity = identityWith(storedCredential);
    identity.setUserId(1L);
    identity.setIdentifier("DEMO:superAdmin");

    when(userMapper.selectList(any())).thenReturn(java.util.List.of(superAdmin));
    when(authIdentityMapper.selectOne(any())).thenReturn(identity);

    AuthenticationResult result = strategy.authenticate(
      params(ORG_CODE, "superAdmin", frontHash));

    assertThat(result.userId()).isEqualTo(1L);
    assertThat(result.organizationId()).isEqualTo(5L);
    assertThat(result.orgType()).isEqualTo("ENTERPRISE");
  }

  @Test
  @DisplayName("superAdmin 主体不唯一 → 401 失败关闭")
  void authenticate_multipleSuperAdmins_unauthorized() {
    SysUser first = enabledUser(1L, 1L);
    first.setUserCode("superAdmin");
    SysUser second = enabledUser(9L, 9L);
    second.setUserCode("superAdmin");
    when(userMapper.selectList(any())).thenReturn(java.util.List.of(first, second));

    assertBusinessError(
      () -> strategy.authenticate(params(ORG_CODE, "superAdmin", frontHash)),
      HttpStatus.UNAUTHORIZED, 1002, FAIL_MESSAGE);

    verify(authIdentityMapper, never()).selectOne(any());
  }

  @Test
  @DisplayName("企业编码为空 → 400/1001，且不查库")
  void authenticate_blankOrgCode_badRequest() {
    assertBusinessError(() -> strategy.authenticate(params(null, USER_CODE, frontHash)),
                        HttpStatus.BAD_REQUEST, 1001, "企业编码、账号或密码不能为空");

    verify(organizationMapper, never()).selectByOrgCode(any());
    verify(authIdentityMapper, never()).selectOne(any());
    }

    @Test
    @DisplayName("密码为空 → 400/1001，且不查库")
    void authenticate_blankPassword_badRequest() {
      assertBusinessError(() -> strategy.authenticate(params(ORG_CODE, USER_CODE, null)),
                          HttpStatus.BAD_REQUEST, 1001, "企业编码、账号或密码不能为空");

      verify(organizationMapper, never()).selectByOrgCode(any());
        verify(authIdentityMapper, never()).selectOne(any());
    }

    // ---------- helpers ----------

  private static Organization enabledTenant(Long organizationId) {
    Organization organization = new Organization();
    organization.setOrganizationId(organizationId);
    organization.setOrgCode(ORG_CODE);
    organization.setOrgType("ENTERPRISE");
    organization.setStatus(1);
    return organization;
  }

  private static SysUser enabledUser(Long userId, Long organizationId) {
        SysUser user = new SysUser();
        user.setUserId(userId);
    user.setOrganizationId(organizationId);
    user.setUserCode(USER_CODE);
        user.setStatus(1);
        return user;
    }

    private static SysAuthIdentity identityWith(String credential) {
        SysAuthIdentity identity = new SysAuthIdentity();
        identity.setUserId(2L);
        identity.setIdentityType("local");
      identity.setIdentifier(IDENTIFIER);
        identity.setCredential(credential);
        return identity;
    }

  private static Map<String, String> params(String orgCode, String userCode, String password) {
        Map<String, String> m = new HashMap<>();
    m.put("orgCode", orgCode);
        m.put("userCode", userCode);
        m.put("password", password);
        return m;
    }

    private static void assertBusinessError(org.assertj.core.api.ThrowableAssert.ThrowingCallable call,
                                            HttpStatus status, int code, String message) {
        assertThatThrownBy(call)
            .isInstanceOfSatisfying(BusinessException.class, ex -> {
                assertThat(ex.getStatus()).isEqualTo(status);
                assertThat(ex.getCode()).isEqualTo(code);
            })
            .hasMessage(message);
    }

    private static String sha256(String raw) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    sb.append('0');
                }
                sb.append(hex);
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

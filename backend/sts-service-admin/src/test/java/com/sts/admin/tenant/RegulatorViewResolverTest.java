package com.sts.admin.tenant;

import com.sts.common.exception.BusinessException;
import com.sts.org.entity.Organization;
import com.sts.org.mapper.OrganizationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 切换视角的准入判定（design §4.2）。
 */
class RegulatorViewResolverTest {

  private OrganizationMapper organizationMapper;
  private RegulatorViewResolver resolver;

  @BeforeEach
  void setUp() {
    organizationMapper = mock(OrganizationMapper.class);
    resolver = new RegulatorViewResolver(organizationMapper);
  }

  private static Organization tenant(String orgType, Integer status) {
    Organization org = new Organization();
    org.setOrganizationId(42L);
    org.setOrgType(orgType);
    org.setStatus(status);
    return org;
  }

  @Test
  @DisplayName("企业租户提交切换视角头返回 403，且不查询目标租户")
  void enterpriseTenant_isRejectedWith403() {
    assertThatThrownBy(() -> resolver.resolveTargetTenant("42", "ENTERPRISE", "GET"))
      .isInstanceOf(BusinessException.class)
      .hasFieldOrPropertyWithValue("code", 4061);

    verifyNoInteractions(organizationMapper);
  }

  @Test
  @DisplayName("租户类型缺失同样按非监管处理，MUST NOT 放行")
  void unknownOrgType_isRejected() {
    assertThatThrownBy(() -> resolver.resolveTargetTenant("42", null, "GET"))
      .isInstanceOf(BusinessException.class)
      .hasFieldOrPropertyWithValue("code", 4061);
  }

  @Test
  @DisplayName("监管切换视角下的写操作返回 403：监管为只读")
  void writeMethodUnderSwitchedView_isRejected() {
    for (String method : new String[]{"POST", "PUT", "DELETE", "PATCH"}) {
      assertThatThrownBy(() -> resolver.resolveTargetTenant("42", "REGULATOR", method))
        .describedAs("方法 %s 应被拒绝", method)
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("code", 4062);
    }
    verifyNoInteractions(organizationMapper);
  }

  @Test
  @DisplayName("监管读取存在且启用的企业租户，返回目标租户 ID")
  void regulatorReadingEnabledEnterprise_resolvesTarget() {
    when(organizationMapper.selectById(42L)).thenReturn(tenant("ENTERPRISE", 1));

    assertThat(resolver.resolveTargetTenant(" 42 ", "REGULATOR", "GET")).isEqualTo(42L);
  }

  @Test
  @DisplayName("监管读取存在且启用的培训机构，返回目标租户 ID")
  void regulatorReadingEnabledTrainingInstitution_resolvesTarget() {
    when(organizationMapper.selectById(42L)).thenReturn(tenant("TRAINING_INSTITUTION", 1));

    assertThat(resolver.resolveTargetTenant("42", "REGULATOR", "GET")).isEqualTo(42L);
  }

  @Test
  @DisplayName("目标租户不存在、已停用或不是企业租户，返回同一话术不泄露差异")
  void missingDisabledOrNonEnterpriseTarget_shareTheSameMessage() {
    when(organizationMapper.selectById(42L)).thenReturn(null);
    BusinessException missing = catchBusiness(() -> resolver.resolveTargetTenant("42", "REGULATOR", "GET"));

    when(organizationMapper.selectById(42L)).thenReturn(tenant("ENTERPRISE", 0));
    BusinessException disabled = catchBusiness(() -> resolver.resolveTargetTenant("42", "REGULATOR", "GET"));

    when(organizationMapper.selectById(42L)).thenReturn(tenant("REGULATOR", 1));
    BusinessException notEnterprise = catchBusiness(() -> resolver.resolveTargetTenant("42", "REGULATOR", "GET"));

    assertThat(missing.getCode()).isEqualTo(4063);
    assertThat(disabled.getMessage())
      .describedAs("停用与不存在必须同一话术，否则可探测租户是否存在")
      .isEqualTo(missing.getMessage());
    assertThat(notEnterprise.getMessage()).isEqualTo(missing.getMessage());
  }

  @Test
  @DisplayName("非数字的目标租户 ID 返回 400")
  void malformedHeader_returns400() {
    assertThatThrownBy(() -> resolver.resolveTargetTenant("abc", "REGULATOR", "GET"))
      .isInstanceOf(BusinessException.class)
      .hasFieldOrPropertyWithValue("code", 4064);
  }

  private static BusinessException catchBusiness(Runnable action) {
    try {
      action.run();
      throw new AssertionError("应当抛出 BusinessException");
    } catch (BusinessException ex) {
      return ex;
    }
  }
}

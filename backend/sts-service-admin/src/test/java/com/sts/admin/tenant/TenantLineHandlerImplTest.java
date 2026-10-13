package com.sts.admin.tenant;

import com.sts.common.exception.BusinessException;
import net.sf.jsqlparser.expression.LongValue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 租户条件注入策略单测（零 DB）。
 * <p>
 * 覆盖隔离列、豁免名单、superAdmin 旁路，以及最关键的一条：
 * HTTP 请求线程缺失上下文时必须拒绝执行，而不是省略条件退化为全表访问。
 */
class TenantLineHandlerImplTest {

  private final TenantLineHandlerImpl handler = new TenantLineHandlerImpl();

  @AfterEach
  void tearDown() {
    TenantContext.clear();
    RequestContextHolder.resetRequestAttributes();
  }

  private static void givenHttpRequestThread() {
    RequestContextHolder.setRequestAttributes(
      new ServletRequestAttributes(new MockHttpServletRequest()));
  }

  @Test
  void tenantIdColumn_isOrganizationId() {
    assertThat(handler.getTenantIdColumn()).isEqualTo("organization_id");
  }

  @Test
  void getTenantId_returnsCurrentTenant() {
    TenantContext.setTenantId(42L);

    assertThat(handler.getTenantId()).isInstanceOfSatisfying(
      LongValue.class, value -> assertThat(value.getValue()).isEqualTo(42L));
  }

  @Test
  void getTenantId_withoutContext_throwsInsteadOfReturningNull() {
    assertThatThrownBy(handler::getTenantId)
      .isInstanceOfSatisfying(BusinessException.class, ex -> assertThat(ex.getCode()).isEqualTo(4050));
  }

  @Test
  void ignoreTable_exemptTablesAreNeverFiltered() {
    TenantContext.setTenantId(42L);

    assertThat(TenantLineHandlerImpl.EXEMPT_TABLES).hasSize(8);
    for (String exempt : TenantLineHandlerImpl.EXEMPT_TABLES) {
      assertThat(handler.ignoreTable(exempt)).describedAs("豁免表 %s", exempt).isTrue();
    }
  }

  @Test
  void ignoreTable_retiredGrantTableIsNotExempt() {
    TenantContext.setTenantId(42L);

    assertThat(handler.ignoreTable("sts_common_resource_org_grant")).isFalse();
  }

  @Test
  void ignoreTable_businessTableIsFilteredWhenTenantBound() {
    TenantContext.setTenantId(42L);

    assertThat(handler.ignoreTable("sts_course_course")).isFalse();
    assertThat(handler.ignoreTable("us_sys_user")).isFalse();
  }

  @Test
  void ignoreTable_isCaseInsensitiveAndTolerantToQuoting() {
    TenantContext.setTenantId(42L);

    assertThat(handler.ignoreTable("`US_SYS_MENU`")).isTrue();
  }

  @Test
  void ignoreTable_superAdminBypassesEveryTable() {
    TenantContext.bypass();

    assertThat(handler.ignoreTable("sts_course_course")).isTrue();
    assertThat(handler.ignoreTable("us_sys_user")).isTrue();
  }

  @Test
  void ignoreTable_httpThreadWithoutContext_refusesToSkipFilter() {
    givenHttpRequestThread();

    // 不忽略 → 随后 getTenantId() 抛出，绝不生成无租户条件的 SQL
    assertThat(handler.ignoreTable("sts_course_course")).isFalse();
    assertThatThrownBy(handler::getTenantId).isInstanceOf(BusinessException.class);
  }

  @Test
  void ignoreTable_nonWebThreadWithoutContext_isAllowed() {
    // 应用启动、集成测试等非 Web 场景放行；处理业务数据的后台任务须显式声明租户
    assertThat(handler.ignoreTable("sts_course_course")).isTrue();
  }
}

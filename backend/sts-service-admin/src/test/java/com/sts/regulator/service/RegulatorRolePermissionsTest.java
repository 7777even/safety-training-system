package com.sts.regulator.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 监管角色权限集护栏（design §4.5）。
 * <p>
 * 监管租户不承载业务，靠"不分配业务写权限码"屏蔽而非各业务域物理拦截，
 * 因此这份清单一旦被误加写权限，监管租户就能在自身租户内创建业务数据。
 * 本测试就是 design 中约定的那道断言。
 */
class RegulatorRolePermissionsTest {

  @Test
  @DisplayName("业务域权限码全部为只读，不含任何写动作")
  void businessPermissions_containNoWriteAction() {
    assertThat(RegulatorRolePermissions.BUSINESS_READ_ONLY)
      .allSatisfy(perms -> assertThat(RegulatorRolePermissions.WRITE_ACTION.matcher(perms).matches())
        .describedAs("业务权限码 %s 含写动作，监管租户将可写业务数据", perms)
        .isFalse());
  }

  @Test
  @DisplayName("权限集中出现写动作的，只能是监管自身域")
  void writeActions_areLimitedToRegulatorOwnDomain() {
    assertThat(RegulatorRolePermissions.all())
      .filteredOn(perms -> RegulatorRolePermissions.WRITE_ACTION.matcher(perms).matches())
      .allSatisfy(perms -> assertThat(RegulatorRolePermissions.isRegulatorOwn(perms))
        .describedAs("写权限 %s 不属于 regulation:/regulator: 前缀", perms)
        .isTrue());
  }

  @Test
  @DisplayName("不含课程、考试、题库等业务域的任何非 list 权限")
  void doesNotContainBusinessDomainNonListPermissions() {
    assertThat(RegulatorRolePermissions.all())
      .filteredOn(perms -> !RegulatorRolePermissions.isRegulatorOwn(perms))
      .filteredOn(perms -> !perms.startsWith("ops:"))
      .allSatisfy(perms -> assertThat(perms)
        .describedAs("业务域权限 %s 必须是 list 查询", perms)
        .endsWith(":list"));
  }

  @Test
  @DisplayName("不含已删除的通用 OPS 统计、导出和规则引擎权限")
  void all_excludesRetiredOpsAndRulePermissions() {
    assertThat(RegulatorRolePermissions.all())
      .doesNotContain("ops:stats:view", "ops:export:list", "ops:workbench:teacher",
                      "ops:workbench:learner", "regulation:rule:list", "regulation:warning:detect")
      .contains("regulation:learning:view", "regulation:warning:export", "system:tenant:add");
  }

  @Test
  @DisplayName("权限集不为空且三段来源都被纳入")
  void allCoversEveryDeclaredGroup() {
    assertThat(RegulatorRolePermissions.all())
      .containsAll(RegulatorRolePermissions.REGULATION_OWN)
      .containsAll(RegulatorRolePermissions.REGULATOR_CHANNEL)
      .containsAll(RegulatorRolePermissions.BUSINESS_READ_ONLY);
  }
}

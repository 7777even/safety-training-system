package com.sts.regulator.service;

import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 监管平台租户内置角色的固定权限集。
 * <p>
 * {@code REGULATOR} 租户不承载课程、考试、培训等业务，通过不分配业务写权限码屏蔽。
 * 允许的三类：监管自身读写、跨租户只读、业务域只读下钻，以及机构主数据维护（功能项 4）。
 */
public final class RegulatorRolePermissions {

  public static final Set<String> REGULATOR_OWN_PREFIXES = Set.of("regulation:", "regulator:", "system:tenant:");

  public static final Pattern WRITE_ACTION = Pattern.compile(
    ".*:(add|create|edit|update|delete|remove|import|publish|withdraw|offline|issue|revoke"
      + "|reissue|submit|assign|close|reopen|ignore|replace|upload|export|calculate|confirm"
      + "|seal|archive|grant|reset|bind|unbind|enable|disable|detect|action|comment)$");

  public static final Set<String> REGULATION_OWN = Set.of(
    "regulation:overview:view",
    "regulation:learning:view",
    "regulation:learning:export",
    "regulation:warning:list",
    "regulation:warning:export",
    "regulation:warning:action",
    "system:tenant:list",
    "system:tenant:add",
    "system:tenant:edit"
  );

  public static final Set<String> REGULATOR_CHANNEL = Set.of(
    "regulator:aggregate:view"
  );

  public static final Set<String> BUSINESS_READ_ONLY = Set.of(
    "course:course:list",
    "exam:exam:list",
    "training:plan:list",
    "training:standard:list",
    "archive:training:list",
    "certificate:cert:list"
  );

  public static Set<String> all() {
    return Stream.of(REGULATION_OWN, REGULATOR_CHANNEL, BUSINESS_READ_ONLY)
      .flatMap(Set::stream)
      .collect(Collectors.toUnmodifiableSet());
  }

  public static boolean isRegulatorOwn(String perms) {
    return REGULATOR_OWN_PREFIXES.stream().anyMatch(perms::startsWith);
  }

  private RegulatorRolePermissions() {
  }
}

package com.sts.admin.tenant;

import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.Set;

/**
 * 租户条件注入策略。
 * <p>
 * 隔离列统一为 {@code organization_id}。豁免表清单 MUST 精确等于
 * openspec/changes/2026-08-06-rebuild-multi-tenant-isolation/design.md §3.2 的 B 类表；
 * 新增豁免必须先在 design 记录理由并经 Review。
 */
@Slf4j
public class TenantLineHandlerImpl implements TenantLineHandler {

  /**
   * 租户隔离列，全部 A 类业务表共用。
   */
  public static final String TENANT_COLUMN = "organization_id";

  /**
   * 豁免表：不参与自动隔离。每一张都在 design §3.2 记录了理由。
   */
  public static final Set<String> EXEMPT_TABLES = Set.of(
    // 租户主数据自身
    "sts_org_organization",
    // 菜单为全局定义，租户通过角色授权菜单子集
    "us_sys_menu",
    // 系统字典与配置，全平台统一维护
    "us_sys_dict_type",
    "us_sys_dict_data",
    "us_sys_config",
    // 登录阶段尚无租户上下文，隔离由 identifier 复合值保证
    "us_sys_auth_identity",
    // 纯关联表，两端主表均已隔离
    "us_sys_role_menu",
    "us_sys_user_role"
  );

  @Override
  public Expression getTenantId() {
    return new LongValue(TenantContext.requireTenantId());
  }

  @Override
  public String getTenantIdColumn() {
    return TENANT_COLUMN;
  }

  @Override
  public boolean ignoreTable(String tableName) {
    String normalized = normalize(tableName);
    if (EXEMPT_TABLES.contains(normalized)) {
      return true;
    }
    // 已登记的系统任务或平台服务显式旁路：跳过全部表的租户条件。
    if (TenantContext.isBypass()) {
      return true;
    }
    if (TenantContext.isPresent()) {
      return false;
    }
    // 上下文缺失。在 HTTP 请求线程中这属于配置错误（Filter 未覆盖该路径），
    // 此时 MUST NOT 放行——返回 false 会让 getTenantId() 抛出，避免退化为全表访问。
    if (RequestContextHolder.getRequestAttributes() != null) {
      log.error("HTTP 请求线程缺失租户上下文，拒绝对表 {} 执行未隔离访问", normalized);
      return false;
    }
    // 非 Web 场景（应用启动、集成测试、未显式声明租户的后台任务）：放行并留痕。
    // 处理业务数据的后台任务 MUST 用 TenantContext.callWithTenant 显式声明租户，见 design §2.2。
    log.debug("非 Web 线程未声明租户上下文，表 {} 本次不注入租户条件", normalized);
    return true;
  }

  private static String normalize(String tableName) {
    if (tableName == null) {
      return "";
    }
    return tableName.replace("`", "").trim().toLowerCase();
  }
}

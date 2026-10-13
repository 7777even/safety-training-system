package com.sts.admin.tenant;

import com.sts.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * 请求级租户上下文。
 * <p>
 * 数据访问层据此自动注入 {@code organization_id} 条件，业务代码无需手写租户过滤。
 * 三种状态：
 * <ul>
 *   <li><b>TENANT</b>：绑定具体租户，全部受隔离的表按该租户过滤；</li>
 *   <li><b>BYPASS</b>：已登记的系统任务或平台服务在最小范围显式旁路，不注入租户条件；</li>
 *   <li><b>ABSENT</b>：未设置。HTTP 请求线程中出现 ABSENT 属于配置错误，
 *       {@link TenantLineHandlerImpl} 会拒绝执行而不是静默省略条件。</li>
 * </ul>
 * <p>
 * 使用方 MUST 在 {@code finally} 中调用 {@link #clear()}，否则线程池复用会串租户。
 * 详见 openspec/changes/2026-08-06-rebuild-multi-tenant-isolation/design.md §2、§3。
 */
public final class TenantContext {

  private static final ThreadLocal<Holder> CONTEXT = new ThreadLocal<>();

  private TenantContext() {
  }

  private record Holder(Long tenantId, boolean bypass) {
  }

  /**
   * 绑定当前线程的租户。
   */
  public static void setTenantId(Long tenantId) {
    if (tenantId == null) {
      throw new IllegalArgumentException("租户 ID 不能为空");
    }
    CONTEXT.set(new Holder(tenantId, false));
  }

  /**
   * 开启显式全局旁路，仅供已登记的系统任务或平台服务在最小范围使用。
   */
  public static void bypass() {
    CONTEXT.set(new Holder(null, true));
  }

  public static boolean isBypass() {
    Holder holder = CONTEXT.get();
    return holder != null && holder.bypass();
  }

  /**
   * 是否已设置上下文（TENANT 或 BYPASS）。
   */
  public static boolean isPresent() {
    return CONTEXT.get() != null;
  }

  /**
   * 返回当前租户；未绑定具体租户时抛出，禁止退化为无条件查询。
   */
  public static Long requireTenantId() {
    Holder holder = CONTEXT.get();
    if (holder == null || holder.tenantId() == null) {
      throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, 4050,
                                  "租户上下文缺失，拒绝执行未隔离的数据访问");
    }
    return holder.tenantId();
  }

  /**
   * 返回当前租户，未设置时返回 {@code null}；仅供上下文判定使用，不用于拼装查询条件。
   */
  public static Long currentTenantIdOrNull() {
    Holder holder = CONTEXT.get();
    return holder == null ? null : holder.tenantId();
  }

  public static void clear() {
    CONTEXT.remove();
  }

  /**
   * 在指定租户下执行，执行完毕后恢复原有上下文。供异步任务、定时任务与监管视角切换使用。
   */
  public static <T> T callWithTenant(Long tenantId, java.util.function.Supplier<T> action) {
    Holder previous = CONTEXT.get();
    try {
      setTenantId(tenantId);
      return action.get();
    } finally {
      restore(previous);
    }
  }

  /**
   * 在指定租户下执行无返回值动作，执行完毕后恢复原有上下文。
   * <p>
   * 供异步任务、事件回调等自带租户信息的场景使用，避免为了套 {@code Supplier} 而改写方法体。
   */
  public static void runWithTenant(Long tenantId, Runnable action) {
    Holder previous = CONTEXT.get();
    try {
      setTenantId(tenantId);
      action.run();
    } finally {
      restore(previous);
    }
  }

  /**
   * 在全局旁路下执行，执行完毕后恢复原有上下文。仅供运维与系统初始化路径使用。
   */
  public static <T> T callWithBypass(java.util.function.Supplier<T> action) {
    Holder previous = CONTEXT.get();
    try {
      bypass();
      return action.get();
    } finally {
      restore(previous);
    }
  }

  private static void restore(Holder previous) {
    if (previous == null) {
      CONTEXT.remove();
    } else {
      CONTEXT.set(previous);
    }
  }
}

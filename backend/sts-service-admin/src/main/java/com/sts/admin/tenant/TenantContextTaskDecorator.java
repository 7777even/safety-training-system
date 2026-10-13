package com.sts.admin.tenant;

import org.springframework.core.task.TaskDecorator;

/**
 * 让异步任务继承提交时的租户上下文，并在任务结束后清理。
 * <p>
 * 没有这个装饰器时，异步任务会因为缺失上下文而无法访问受隔离的表；
 * 而线程池复用又会让上一个任务残留的 {@code ThreadLocal} 被下一个任务读到，造成串租户。
 * 两个风险都由本类消除。详见 design.md §2.2。
 */
public class TenantContextTaskDecorator implements TaskDecorator {

  @Override
  public Runnable decorate(Runnable runnable) {
    // 在提交线程（通常是 HTTP 线程）读取，快照随任务一起转移到工作线程。
    boolean bypass = TenantContext.isBypass();
    Long tenantId = TenantContext.currentTenantIdOrNull();
    return () -> {
      try {
        if (bypass) {
          TenantContext.bypass();
        } else if (tenantId != null) {
          TenantContext.setTenantId(tenantId);
        }
        runnable.run();
      } finally {
        TenantContext.clear();
      }
    };
  }
}

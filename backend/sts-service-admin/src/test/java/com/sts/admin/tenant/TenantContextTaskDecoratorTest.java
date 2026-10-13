package com.sts.admin.tenant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 异步任务上下文传递单测（零 DB）。
 * <p>
 * 两个必须成立的性质：任务能继承提交时的租户；任务结束后线程不残留上下文（否则线程池复用会串租户）。
 */
class TenantContextTaskDecoratorTest {

  private final TenantContextTaskDecorator decorator = new TenantContextTaskDecorator();

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void decorate_carriesTenantIntoWorkerThread() {
    TenantContext.setTenantId(9L);
    AtomicReference<Long> seen = new AtomicReference<>();

    Runnable task = decorator.decorate(() -> seen.set(TenantContext.requireTenantId()));
    TenantContext.clear();  // 模拟提交后原线程已结束请求
    task.run();

    assertThat(seen.get()).isEqualTo(9L);
  }

  @Test
  void decorate_carriesBypassIntoWorkerThread() {
    TenantContext.bypass();
    AtomicBoolean seen = new AtomicBoolean();

    Runnable task = decorator.decorate(() -> seen.set(TenantContext.isBypass()));
    TenantContext.clear();
    task.run();

    assertThat(seen.get()).isTrue();
  }

  @Test
  void decorate_clearsContextAfterTask() {
    TenantContext.setTenantId(9L);

    Runnable task = decorator.decorate(() -> {
    });
    TenantContext.clear();
    task.run();

    assertThat(TenantContext.isPresent()).describedAs("任务结束必须清理，否则线程池复用会串租户").isFalse();
  }

  @Test
  void decorate_clearsContextEvenWhenTaskThrows() {
    TenantContext.setTenantId(9L);
    Runnable task = decorator.decorate(() -> {
      throw new IllegalStateException("boom");
    });
    TenantContext.clear();

    try {
      task.run();
    } catch (IllegalStateException ignored) {
      // 预期抛出
    }

    assertThat(TenantContext.isPresent()).isFalse();
  }

  @Test
  void decorate_withoutSubmitterContext_leavesWorkerClean() {
    Runnable task = decorator.decorate(() -> {
    });
    task.run();

    assertThat(TenantContext.isPresent()).isFalse();
  }
}

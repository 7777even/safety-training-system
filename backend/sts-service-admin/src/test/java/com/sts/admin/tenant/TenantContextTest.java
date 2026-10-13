package com.sts.admin.tenant;

import com.sts.common.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 租户上下文单测（零 DB）。上下文泄漏是多租户最危险的缺陷，这里覆盖设置、清理与嵌套恢复。
 */
class TenantContextTest {

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void setTenantId_bindsCurrentThread() {
    TenantContext.setTenantId(7L);

    assertThat(TenantContext.isPresent()).isTrue();
    assertThat(TenantContext.isBypass()).isFalse();
    assertThat(TenantContext.requireTenantId()).isEqualTo(7L);
  }

  @Test
  void setTenantId_rejectsNull() {
    assertThatThrownBy(() -> TenantContext.setTenantId(null))
      .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void requireTenantId_withoutContext_throws() {
    assertThatThrownBy(TenantContext::requireTenantId)
      .isInstanceOfSatisfying(BusinessException.class, ex -> assertThat(ex.getCode()).isEqualTo(4050));
  }

  @Test
  void requireTenantId_underBypass_throwsBecauseBypassHasNoTenant() {
    TenantContext.bypass();

    assertThat(TenantContext.isBypass()).isTrue();
    assertThatThrownBy(TenantContext::requireTenantId).isInstanceOf(BusinessException.class);
  }

  @Test
  void clear_removesContext() {
    TenantContext.setTenantId(7L);
    TenantContext.clear();

    assertThat(TenantContext.isPresent()).isFalse();
    assertThat(TenantContext.currentTenantIdOrNull()).isNull();
  }

  @Test
  void callWithTenant_restoresPreviousContext() {
    TenantContext.setTenantId(1L);

    Long inner = TenantContext.callWithTenant(2L, TenantContext::requireTenantId);

    assertThat(inner).isEqualTo(2L);
    assertThat(TenantContext.requireTenantId()).describedAs("嵌套调用后必须恢复原租户").isEqualTo(1L);
  }

  @Test
  void callWithTenant_withoutPreviousContext_leavesNothingBehind() {
    TenantContext.callWithTenant(2L, () -> null);

    assertThat(TenantContext.isPresent()).describedAs("原本无上下文，调用后不得残留").isFalse();
  }

  @Test
  void callWithBypass_restoresPreviousContext() {
    TenantContext.setTenantId(1L);

    Boolean inner = TenantContext.callWithBypass(TenantContext::isBypass);

    assertThat(inner).isTrue();
    assertThat(TenantContext.isBypass()).isFalse();
    assertThat(TenantContext.requireTenantId()).isEqualTo(1L);
  }

  @Test
  void callWithTenant_restoresEvenWhenActionThrows() {
    TenantContext.setTenantId(1L);

    assertThatThrownBy(() -> TenantContext.callWithTenant(2L, () -> {
      throw new IllegalStateException("boom");
    })).isInstanceOf(IllegalStateException.class);

    assertThat(TenantContext.requireTenantId()).isEqualTo(1L);
  }

  @Test
  void context_isNotSharedAcrossThreads() throws Exception {
    TenantContext.setTenantId(1L);
    ExecutorService pool = Executors.newSingleThreadExecutor();
    try {
      Future<Boolean> otherThreadSeesNothing = pool.submit(() -> !TenantContext.isPresent());

      assertThat(otherThreadSeesNothing.get()).isTrue();
    } finally {
      pool.shutdown();
    }
  }
}

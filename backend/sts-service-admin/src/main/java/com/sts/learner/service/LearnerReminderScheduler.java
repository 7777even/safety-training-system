package com.sts.learner.service;

import com.sts.admin.tenant.TenantContext;
import com.sts.learner.mapper.LearnerNotificationSourceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;

/** 已登记的系统任务：只读租户主数据，随后逐租户生成提醒，不使用全局业务旁路。 */
@Component @RequiredArgsConstructor @Slf4j
public class LearnerReminderScheduler {
  /** 租户目录。 */
  private final LearnerNotificationSourceMapper sources;
  /** 每个租户独立事务生成提醒。 */
  private final LearnerNotificationService notifications;

  /** 每分钟进入提醒窗口；唯一事件键支持恢复、重试和多实例。 */
  @Scheduled(fixedDelay = 60000, initialDelay = 15000)
  public void remind() {
    for (Long org : sources.tenants()) {
      try { TenantContext.runWithTenant(org, () -> notifications.scan(LocalDateTime.now())); }
      catch (RuntimeException e) { log.error("学员提醒扫描失败，机构={}", org, e); }
    }
  }
}

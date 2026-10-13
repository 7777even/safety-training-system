package com.sts.exam.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sts.admin.tenant.TenantContext;
import com.sts.exam.entity.ExamAttempt;
import com.sts.exam.mapper.ExamAttemptMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/** 扫描服务端截止时间已到但仍在作答的尝试，避免依赖浏览器保持打开。 */
@Component
public class ExamAttemptTimeoutScheduler {
  private final ExamAttemptMapper attemptMapper;
  private final ExamAttemptService attemptService;

  public ExamAttemptTimeoutScheduler(ExamAttemptMapper attemptMapper, ExamAttemptService attemptService) {
    this.attemptMapper = attemptMapper;
    this.attemptService = attemptService;
  }

  /** 调度线程旁路读取全租户数据，写入时恢复每条作答所属租户。 */
  @Scheduled(fixedDelayString = "${exam.attempt.timeout-scan-interval-ms:60000}")
  public int timeoutExpiredAttempts() {
    List<ExamAttempt> attempts = TenantContext.callWithBypass(() -> attemptMapper.selectList(
      new LambdaQueryWrapper<ExamAttempt>()
        .eq(ExamAttempt::getAttemptStatus, "ANSWERING")
        .isNotNull(ExamAttempt::getDeadlineTime)
        .le(ExamAttempt::getDeadlineTime, LocalDateTime.now())));
    int completed = 0;
    for (ExamAttempt attempt : attempts) {
      if (attempt.getExamAttemptId() == null || attempt.getOrganizationId() == null) continue;
      try {
        TenantContext.callWithTenant(attempt.getOrganizationId(), () -> {
          attemptService.timeoutSubmit(attempt.getExamAttemptId());
          return null;
        });
        completed++;
      } catch (RuntimeException ignored) {
        // 下一轮重试；未成功收卷时不能伪造已完成结果。
      }
    }
    return completed;
  }
}

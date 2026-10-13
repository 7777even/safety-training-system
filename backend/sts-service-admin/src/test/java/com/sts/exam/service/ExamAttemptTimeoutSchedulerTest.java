package com.sts.exam.service;

import com.sts.exam.entity.ExamAttempt;
import com.sts.exam.mapper.ExamAttemptMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExamAttemptTimeoutSchedulerTest {

  @Test
  void timeoutExpiredAttempts_submitsEveryExpiredAnsweringAttemptInItsTenant() {
    ExamAttemptMapper mapper = mock(ExamAttemptMapper.class);
    ExamAttemptService attemptService = mock(ExamAttemptService.class);
    ExamAttempt attempt = new ExamAttempt();
    attempt.setExamAttemptId(44L);
    attempt.setOrganizationId(1L);
    when(mapper.selectList(any())).thenReturn(List.of(attempt));
    ExamAttemptTimeoutScheduler scheduler = new ExamAttemptTimeoutScheduler(mapper, attemptService);

    int completed = scheduler.timeoutExpiredAttempts();

    assertThat(completed).isEqualTo(1);
    verify(attemptService).timeoutSubmit(44L);
  }
}

package com.sts.learner.service;

import com.sts.admin.tenant.TenantContext;
import com.sts.learner.mapper.LearnerMessageMapper;
import com.sts.learner.entity.LearnerMessage;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** 站内消息公开行为：唯一来源、实际接收人和提醒窗口。 */
class LearnerMessageServiceTest {
  @Test void repeatedAssignmentUsesSameEventAndDeduplicatesRecipients() {
    var mapper = mock(LearnerMessageMapper.class);
    doAnswer(i -> { ((LearnerMessage)i.getArgument(0)).setMessageId(1L); return 1; }).when(mapper).save(any());
    var service = new LearnerMessageService(mapper);
    TenantContext.runWithTenant(10L, () -> {
      service.send("course:31", "COURSE", "安全学习", "已安排课程", 31L, null, null, List.of(7L, 7L, 8L));
      service.send("course:31", "COURSE", "安全学习", "已安排课程", 31L, null, null, List.of(7L));
    });
    var captor = org.mockito.ArgumentCaptor.forClass(LearnerMessage.class);
    verify(mapper, times(2)).save(captor.capture());
    assertThat(captor.getAllValues()).allSatisfy(m -> {
      assertThat(m.getEventKey()).isEqualTo("course:31"); assertThat(m.getOrganizationId()).isEqualTo(10L);
    });
    verify(mapper, times(2)).addRecipient(10L, 1L, 7L);
    verify(mapper).addRecipient(10L, 1L, 8L);
  }
  @Test void deadlineWindowExcludesExpiredAndCompletedTasks() {
    var now = LocalDateTime.of(2026, 10, 8, 10, 0);
    var service = new LearnerMessageService(mock(LearnerMessageMapper.class));
    assertThat(service.shouldRemind(now, now.plusHours(24), "NOT_STARTED")).isTrue();
    assertThat(service.shouldRemind(now, now.plusHours(25), "LEARNING")).isFalse();
    assertThat(service.shouldRemind(now, now, "LEARNING")).isFalse();
    assertThat(service.shouldRemind(now, now.plusHours(2), "COMPLETED")).isFalse();
    assertThat(service.shouldRemind(now, null, "LEARNING")).isFalse();
  }
}

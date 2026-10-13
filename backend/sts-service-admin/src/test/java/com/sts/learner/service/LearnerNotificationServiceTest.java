package com.sts.learner.service;

import com.sts.admin.tenant.TenantContext;
import com.sts.learner.mapper.LearnerNotificationSourceMapper;
import com.sts.learner.mapper.LearnerNotificationSourceMapper.Target;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** 公共扫描行为验证已交卷/已完成不催办，首次进入窗口仍生成提醒。 */
class LearnerNotificationServiceTest {
  @Test void trainingPublishNotifiesFrozenParticipantsWithStableEventKeys() {
    var sources = mock(LearnerNotificationSourceMapper.class);
    var messages = spy(new LearnerMessageService(mock(com.sts.learner.mapper.LearnerMessageMapper.class)));
    doNothing().when(messages).send(anyString(), anyString(), anyString(), anyString(), anyLong(), nullable(Long.class), nullable(Long.class), any());
    var now = LocalDateTime.of(2026,10,11,10,0);
    when(sources.trainings(10L,99L)).thenReturn(List.of(
      new Target(7L,"TRAINING",71L,99L,null,"岗位培训","PENDING",now,now.plusDays(1)),
      new Target(8L,"TRAINING",81L,99L,null,"岗位培训","PENDING",now,now.plusDays(1))));
    var service = new LearnerNotificationService(sources,messages);
    TenantContext.runWithTenant(10L,() -> { service.businessChanged("training","publish",99L,null); service.businessChanged("training","publish",99L,null); });
    verify(messages,times(2)).send(eq("TRAINING:71:null:assigned"),eq("TRAINING"),eq("岗位培训"),anyString(),eq(71L),eq(99L),isNull(),eq(List.of(7L)));
    verify(messages,times(2)).send(eq("TRAINING:81:null:assigned"),eq("TRAINING"),eq("岗位培训"),anyString(),eq(81L),eq(99L),isNull(),eq(List.of(8L)));
    verify(sources,times(2)).trainings(10L,99L);
    verifyNoMoreInteractions(sources);
  }
  @Test void firstScanCreatesAssignmentAndDeadlineButSkipsSubmittedExam() {
    var sources = mock(LearnerNotificationSourceMapper.class);
    var messages = spy(new LearnerMessageService(mock(com.sts.learner.mapper.LearnerMessageMapper.class)));
    doNothing().when(messages).send(anyString(), anyString(), anyString(), anyString(), anyLong(), nullable(Long.class), nullable(Long.class), any());
    var now = LocalDateTime.of(2026, 10, 8, 10, 0);
    when(sources.courses(10L, null)).thenReturn(List.of(new Target(7L, "COURSE", 31L, null, null, "安全课程", "LEARNING", now.minusDays(1), now.plusHours(3))));
    when(sources.exams(10L, null)).thenReturn(List.of(new Target(7L, "FORMAL_EXAM", 8L, 4L, null, "考试", "PENDING_PUBLISH", now.minusHours(1), now.plusHours(2))));
    when(sources.trainings(10L, null)).thenReturn(List.of()); when(sources.quizzes(10L, null)).thenReturn(List.of());
    TenantContext.runWithTenant(10L, () -> new LearnerNotificationService(sources, messages).scan(now));
    verify(messages).send(eq("COURSE:31:null:assigned"), eq("COURSE"), eq("安全课程"), anyString(), eq(31L), isNull(), isNull(), eq(List.of(7L)));
    verify(messages).send(eq("COURSE:31:null:deadline:2026-10-08T13:00"), eq("COURSE"), anyString(), anyString(), eq(31L), isNull(), isNull(), eq(List.of(7L)));
    verify(messages, never()).send(anyString(), eq("FORMAL_EXAM"), anyString(), anyString(), anyLong(), any(), any(), any());
  }
  @Test void trainingDeadlineUsesCurrentScheduleAndSkipsCompletedExpiredAndDistantTasks() {
    var sources=mock(LearnerNotificationSourceMapper.class);
    var messages=spy(new LearnerMessageService(mock(com.sts.learner.mapper.LearnerMessageMapper.class)));
    doNothing().when(messages).send(anyString(),anyString(),anyString(),anyString(),anyLong(),nullable(Long.class),nullable(Long.class),any());
    var now=LocalDateTime.of(2026,10,11,10,0);
    when(sources.trainings(10L,null)).thenReturn(List.of(
      new Target(7L,"TRAINING",71L,99L,null,"培训","PENDING",now.minusDays(1),now.plusHours(24)),
      new Target(8L,"TRAINING",81L,99L,null,"培训","COMPLETED",now.minusDays(1),now.plusHours(3)),
      new Target(9L,"TRAINING",91L,99L,null,"培训","PENDING",now.minusDays(1),now),
      new Target(10L,"TRAINING",101L,99L,null,"培训","PENDING",now,now.plusHours(25))));
    var service=new LearnerNotificationService(sources,messages);
    TenantContext.runWithTenant(10L,()->service.scan(now));
    verify(messages).send(eq("TRAINING:71:null:deadline:2026-10-12T10:00"),eq("TRAINING"),anyString(),anyString(),eq(71L),eq(99L),isNull(),eq(List.of(7L)));
    verify(messages,never()).send(anyString(),anyString(),anyString(),anyString(),eq(81L),any(),any(),any());
    verify(messages,never()).send(anyString(),anyString(),anyString(),anyString(),eq(91L),any(),any(),any());
    verify(messages,never()).send(contains(":deadline:"),anyString(),anyString(),anyString(),eq(101L),any(),any(),any());
    // 变更后的结束时间进入新事件键，重扫同一时间仍保持同一键。
    when(sources.trainings(10L,null)).thenReturn(List.of(new Target(7L,"TRAINING",71L,99L,null,"培训","PENDING",now,now.plusHours(5))));
    TenantContext.runWithTenant(10L,()->{service.scan(now);service.scan(now);});
    verify(messages,times(2)).send(eq("TRAINING:71:null:deadline:2026-10-11T15:00"),eq("TRAINING"),anyString(),anyString(),eq(71L),eq(99L),isNull(),eq(List.of(7L)));
  }
  @Test void examPublishAndAssignmentUseFrozenCandidateReferencesAndSameKeys() {
    var sources=mock(LearnerNotificationSourceMapper.class);var messages=spy(new LearnerMessageService(mock(com.sts.learner.mapper.LearnerMessageMapper.class)));
    doNothing().when(messages).send(anyString(),anyString(),anyString(),anyString(),anyLong(),nullable(Long.class),nullable(Long.class),any());
    when(sources.exams(10L,99L)).thenReturn(List.of(new Target(7L,"FORMAL_EXAM",71L,99L,null,"岗位考试","NOT_STARTED",null,null)));
    var service=new LearnerNotificationService(sources,messages);
    TenantContext.runWithTenant(10L,()->{service.businessChanged("exam","publish",99L,null);service.businessChanged("exam","bindSelection",99L,null);});
    verify(messages,times(2)).send(eq("FORMAL_EXAM:71:null:assigned"),eq("FORMAL_EXAM"),eq("岗位考试"),anyString(),eq(71L),eq(99L),isNull(),eq(List.of(7L)));
  }
}

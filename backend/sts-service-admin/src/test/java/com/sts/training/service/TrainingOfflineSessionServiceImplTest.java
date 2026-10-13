package com.sts.training.service;

import com.sts.common.exception.BusinessException;
import com.sts.training.dto.TrainingOfflineSessionCreateRequest;
import com.sts.training.dto.TrainingOfflineSessionUpdateRequest;
import com.sts.training.entity.TrainingOfflineSession;
import com.sts.training.entity.TrainingPlan;
import com.sts.training.mapper.TrainingOfflineSessionMapper;
import com.sts.training.mapper.TrainingPlanMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TrainingOfflineSessionServiceImplTest {

  private TrainingOfflineSessionMapper sessionMapper;
  private TrainingPlanMapper planMapper;
  private com.sts.training.mapper.TrainingPlanStageMapper stages;
  private TrainingOfflineSessionServiceImpl service;

  @BeforeEach
  void setUp() {
    com.sts.admin.tenant.TenantContext.setTenantId(1L);
    sessionMapper = mock(TrainingOfflineSessionMapper.class);
    planMapper = mock(TrainingPlanMapper.class);
    stages = mock(com.sts.training.mapper.TrainingPlanStageMapper.class);
    service = new TrainingOfflineSessionServiceImpl(sessionMapper, planMapper, stages, mock(com.sts.training.mapper.TrainingPlanContentMapper.class));
  }

  @org.junit.jupiter.api.AfterEach
  void clearTenant() {com.sts.admin.tenant.TenantContext.clear();}

  @Test
  @DisplayName("start：SCHEDULED 推进为 IN_PROGRESS")
  void start_scheduled_becomesInProgress() {
    TrainingOfflineSession session = scheduled(1);
    when(sessionMapper.selectById(9L)).thenReturn(session);
    when(sessionMapper.updateById(session)).thenReturn(1);

    service.start(9L, 1, 7L, false);

    assertThat(session.getSessionStatus()).isEqualTo("IN_PROGRESS");
    verify(sessionMapper).updateById(session);
  }

  @Test
  @DisplayName("start：非 SCHEDULED 返回 409/8202")
  void start_inProgress_rejected() {
    TrainingOfflineSession session = scheduled(1);
    session.setSessionStatus("IN_PROGRESS");
    when(sessionMapper.selectById(9L)).thenReturn(session);

    assertThatThrownBy(() -> service.start(9L, 1, 7L, false))
      .isInstanceOf(BusinessException.class)
      .hasFieldOrPropertyWithValue("code", 8202)
      .hasFieldOrPropertyWithValue("status", HttpStatus.CONFLICT);
    verify(sessionMapper, never()).updateById(any(TrainingOfflineSession.class));
  }

  @Test
  @DisplayName("start：非教师且非管理员返回 403/8131")
  void start_otherTeacher_forbidden() {
    when(sessionMapper.selectById(9L)).thenReturn(scheduled(1));

    assertThatThrownBy(() -> service.start(9L, 1, 8L, false))
      .isInstanceOf(BusinessException.class)
      .hasFieldOrPropertyWithValue("code", 8131)
      .hasFieldOrPropertyWithValue("status", HttpStatus.FORBIDDEN);
    verify(sessionMapper, never()).updateById(any(TrainingOfflineSession.class));
  }

  @Test
  @DisplayName("start：管理员可操作非本人场次")
  void start_adminActor_allowsOtherTeacher() {
    TrainingOfflineSession session = scheduled(1);
    when(sessionMapper.selectById(9L)).thenReturn(session);
    when(sessionMapper.updateById(session)).thenReturn(1);

    service.start(9L, 1, 8L, true);

    assertThat(session.getSessionStatus()).isEqualTo("IN_PROGRESS");
  }

  @Test
  @DisplayName("complete：IN_PROGRESS 推进为 COMPLETED")
  void complete_inProgress_becomesCompleted() {
    TrainingOfflineSession session = scheduled(1);
    session.setSessionStatus("IN_PROGRESS");
    when(sessionMapper.selectById(9L)).thenReturn(session);
    when(sessionMapper.updateById(session)).thenReturn(1);

    service.complete(9L, 1, 7L, false);

    assertThat(session.getSessionStatus()).isEqualTo("COMPLETED");
  }

  @Test
  @DisplayName("cancel：SCHEDULED 推进为 CANCELLED")
  void cancel_scheduled_becomesCancelled() {
    TrainingOfflineSession session = scheduled(1);
    when(sessionMapper.selectById(9L)).thenReturn(session);
    when(sessionMapper.updateById(session)).thenReturn(1);

    service.cancel(9L, 1, 7L, false);

    assertThat(session.getSessionStatus()).isEqualTo("CANCELLED");
  }

  @Test
  @DisplayName("update：不修改 sessionStatus")
  void update_doesNotChangeSessionStatus() {
    TrainingOfflineSession session = scheduled(1);
    when(sessionMapper.selectById(9L)).thenReturn(session);
    when(sessionMapper.updateById(session)).thenReturn(1);
    TrainingOfflineSessionUpdateRequest request = new TrainingOfflineSessionUpdateRequest();
    request.setVersion(1);
    request.setSessionName("新名称");
    request.setLocation("车间");
    request.setStartTime(LocalDateTime.of(2026, 9, 9, 9, 0));
    request.setEndTime(LocalDateTime.of(2026, 9, 9, 12, 0));
    request.setStatus(1);
    request.setSessionStatus("COMPLETED");

    service.update(9L, request);

    assertThat(session.getSessionStatus()).isEqualTo("SCHEDULED");
    assertThat(session.getSessionName()).isEqualTo("新名称");
  }

  @Test
  @DisplayName("create：忽略请求状态固定 DRAFT")
  void create_alwaysDraft() {
    when(planMapper.lockForApproval(1L,1L)).thenReturn(new TrainingPlan());
    when(sessionMapper.insert(any(TrainingOfflineSession.class))).thenAnswer(invocation -> {
      TrainingOfflineSession session = invocation.getArgument(0);
      session.setOfflineSessionId(9L);
      return 1;
    });
    TrainingOfflineSessionCreateRequest request = new TrainingOfflineSessionCreateRequest();
    request.setTrainingPlanId(1L);
    request.setPlanStageId(2L);
    var stage = new com.sts.training.entity.TrainingPlanStage();stage.setTrainingPlanId(1L);when(stages.selectById(2L)).thenReturn(stage);
    request.setSessionName("手工场次");
    request.setLocation("车间");
    request.setStartTime(LocalDateTime.of(2026, 9, 9, 9, 0));
    request.setEndTime(LocalDateTime.of(2026, 9, 9, 12, 0));
    request.setSessionStatus("IN_PROGRESS");
    request.setStatus(1);

    service.create(request);

    org.mockito.ArgumentCaptor<TrainingOfflineSession> captor =
      org.mockito.ArgumentCaptor.forClass(TrainingOfflineSession.class);
    verify(sessionMapper).insert(captor.capture());
    assertThat(captor.getValue().getSessionStatus()).isEqualTo("DRAFT");
  }

  @Test
  void createRejectsContentDeletedByApprovedChange() {
    com.sts.admin.tenant.TenantContext.setTenantId(1L);
    try {
      var request = new TrainingOfflineSessionCreateRequest();request.setTrainingPlanId(1L);request.setPlanContentId(99L);
      when(planMapper.lockForApproval(1L,1L)).thenReturn(new TrainingPlan());
      assertThatThrownBy(() -> service.create(request)).isInstanceOf(BusinessException.class).hasMessageContaining("编排");
      verify(planMapper).lockForApproval(1L,1L);verify(sessionMapper,never()).insert(any(TrainingOfflineSession.class));
    } finally {com.sts.admin.tenant.TenantContext.clear();}
  }

  private TrainingOfflineSession scheduled(int version) {
    TrainingOfflineSession session = new TrainingOfflineSession();
    session.setOfflineSessionId(9L);
    session.setTrainingPlanId(1L);
    session.setTeacherUserId(7L);
    session.setSessionStatus("SCHEDULED");
    session.setVersion(version);
    return session;
  }
}

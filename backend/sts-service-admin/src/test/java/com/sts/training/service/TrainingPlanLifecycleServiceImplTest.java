package com.sts.training.service;

import com.sts.common.exception.BusinessException;
import com.sts.regulation.service.PlanHoursWarningRefreshService;
import com.sts.training.entity.*;
import com.sts.training.exception.TrainingPlanEndBlockedException;
import com.sts.training.mapper.*;
import com.sts.training.port.TrainingAssessmentExecutionPort;
import com.sts.training.port.TrainingCourseExecutionPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TrainingPlanLifecycleServiceImplTest {

  private TrainingPlanMapper planMapper;
  private TrainingPlanStageMapper stageMapper;
  private TrainingPlanContentMapper contentMapper;
  private TrainingParticipantMapper participantMapper;
  private TrainingExecutionLinkMapper executionLinkMapper;
  private TrainingOfflineSessionMapper offlineSessionMapper;
  private TrainingParticipantContentResultMapper resultMapper;
  private TrainingCourseExecutionPort coursePort;
  private TrainingAssessmentExecutionPort assessmentPort;
  private TrainingResultSyncService resultSyncService;
  private TrainingPlanLifecycleServiceImpl service;

  @org.junit.jupiter.api.AfterEach void clearTenant() { com.sts.admin.tenant.TenantContext.clear(); }

  @BeforeEach
  void setUp() {
    planMapper = mock(TrainingPlanMapper.class);
    com.sts.admin.tenant.TenantContext.setTenantId(1L);
    when(planMapper.lockForApproval(any(),any())).thenAnswer(inv -> planMapper.selectById(inv.getArgument(0)));
    stageMapper = mock(TrainingPlanStageMapper.class);
    contentMapper = mock(TrainingPlanContentMapper.class);
    participantMapper = mock(TrainingParticipantMapper.class);
    executionLinkMapper = mock(TrainingExecutionLinkMapper.class);
    offlineSessionMapper = mock(TrainingOfflineSessionMapper.class);
    resultMapper = mock(TrainingParticipantContentResultMapper.class);
    coursePort = mock(TrainingCourseExecutionPort.class);
    assessmentPort = mock(TrainingAssessmentExecutionPort.class);
    resultSyncService = mock(TrainingResultSyncService.class);
    service = new TrainingPlanLifecycleServiceImpl(
      planMapper, stageMapper, contentMapper, participantMapper, executionLinkMapper,
      offlineSessionMapper, resultMapper, coursePort, assessmentPort, resultSyncService,
      mock(PlanHoursWarningRefreshService.class));
  }

  @Test
  @DisplayName("start：必修执行未生成则拒绝")
  void start_rejectsMissingRequiredExecution() {
    publishedPlan();
    requiredExamWithoutLink();

    assertThatThrownBy(() -> service.start(1L, 0))
      .isInstanceOf(BusinessException.class)
      .hasFieldOrPropertyWithValue("code", 8162);
    verify(planMapper, never()).updateById(any(TrainingPlan.class));
  }

  @Test
  @DisplayName("start：非必修内容未安排也拒绝开始")
  void start_rejectsMissingOptionalExecution() {
    publishedPlan();
    TrainingPlanStage stage = new TrainingPlanStage();
    stage.setPlanStageId(5L);
    when(stageMapper.selectList(any())).thenReturn(List.of(stage));
    TrainingPlanContent exam = new TrainingPlanContent();
    exam.setPlanContentId(11L);
    exam.setPlanStageId(5L);
    exam.setContentType("EXAM");
    exam.setIsRequired(1);
    TrainingPlanContent offline = new TrainingPlanContent();
    offline.setPlanContentId(12L);
    offline.setPlanStageId(5L);
    offline.setContentType("OFFLINE");
    offline.setIsRequired(0);
    when(contentMapper.selectList(any())).thenReturn(List.of(exam, offline));
    TrainingExecutionLink link = new TrainingExecutionLink();
    link.setPlanContentId(11L);
    link.setExecutionType("EXAM");
    when(executionLinkMapper.selectOne(any())).thenReturn(link);

    assertThatThrownBy(() -> service.start(1L, 0))
      .isInstanceOf(BusinessException.class)
      .hasFieldOrPropertyWithValue("code", 8162);
    verify(planMapper, never()).updateById(any(TrainingPlan.class));
  }

  @Test
  @DisplayName("start：全部内容均已安排后进入执行中")
  void start_startsAfterAllContentsAreArranged() {
    publishedPlan();
    TrainingPlanStage stage = new TrainingPlanStage();
    stage.setPlanStageId(5L);
    when(stageMapper.selectList(any())).thenReturn(List.of(stage));
    TrainingPlanContent exam = new TrainingPlanContent();
    exam.setPlanContentId(11L);
    exam.setPlanStageId(5L);
    exam.setContentType("EXAM");
    exam.setIsRequired(1);
    TrainingPlanContent offline = new TrainingPlanContent();
    offline.setPlanContentId(12L);
    offline.setPlanStageId(5L);
    offline.setContentType("OFFLINE");
    offline.setIsRequired(0);
    when(contentMapper.selectList(any())).thenReturn(List.of(exam, offline));
    TrainingExecutionLink link = new TrainingExecutionLink();
    link.setPlanContentId(11L);
    link.setExecutionType("EXAM");
    when(executionLinkMapper.selectOne(any())).thenReturn(link);
    when(offlineSessionMapper.selectCount(any())).thenReturn(1L);
    when(planMapper.updateById(any(TrainingPlan.class))).thenReturn(1);

    service.start(1L, 0);

    ArgumentCaptor<TrainingPlan> captor = ArgumentCaptor.forClass(TrainingPlan.class);
    verify(planMapper).updateById(captor.capture());
    assertThat(captor.getValue().getPublishStatus()).isEqualTo("RUNNING");
  }

  @Test
  @DisplayName("end：PUBLISHED 直接结束返回 409/8163 且不同步冻结")
  void end_publishedPlan_rejectsWithoutSyncOrFreeze() {
    publishedPlan();

    assertThatThrownBy(() -> service.end(1L, 0))
      .isInstanceOfSatisfying(BusinessException.class, ex -> {
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ex.getCode()).isEqualTo(8163);
        assertThat(ex.getMessage()).contains("进行中");
      });
    verify(resultSyncService, never()).synchronize(any());
    verify(resultSyncService, never()).freezePlan(any());
    verify(planMapper, never()).updateById(any(TrainingPlan.class));
    verify(stageMapper, never()).selectList(any());
    verify(contentMapper, never()).selectList(any());
  }

  @Test
  @DisplayName("end：进行中 Attempt 返回 participantId/contentId")
  void end_rejectsInProgressAttempt() {
    runningPlan();
    TrainingPlanContent exam = requiredExam();
    TrainingExecutionLink link = new TrainingExecutionLink();
    link.setPlanContentId(11L);
    link.setExecutionType("EXAM");
    link.setExecutionId(501L);
    when(executionLinkMapper.selectOne(any())).thenReturn(link);
    when(assessmentPort.listInProgressAttemptParticipantIds(501L)).thenReturn(List.of(2L));
    when(coursePort.listInProgressLearningParticipantIds(any())).thenReturn(List.of());

    assertThatThrownBy(() -> service.end(1L, 0))
      .isInstanceOf(TrainingPlanEndBlockedException.class)
      .satisfies(ex -> {
        TrainingPlanEndBlockedException blocked = (TrainingPlanEndBlockedException) ex;
        assertThat(blocked.getIssues()).anyMatch(issue ->
                                                   Long.valueOf(2L).equals(issue.getTrainingParticipantId())
                                                     && Long.valueOf(11L).equals(issue.getPlanContentId())
                                                     && "IN_PROGRESS_ATTEMPT".equals(issue.getReasonCode()));
      });
    verify(resultSyncService, never()).synchronize(any());
    verify(resultSyncService, never()).freezePlan(any());
    assertThat(exam.getContentType()).isEqualTo("EXAM");
  }

  @Test
  @DisplayName("end：门禁通过后冻结并置 ENDED")
  void end_success_freezesAndEnds() {
    runningPlan();
    requiredExam();
    TrainingExecutionLink link = new TrainingExecutionLink();
    link.setExecutionId(501L);
    link.setPlanContentId(11L);
    link.setExecutionType("EXAM");
    when(executionLinkMapper.selectOne(any())).thenReturn(link);
    when(assessmentPort.listInProgressAttemptParticipantIds(501L)).thenReturn(List.of());
    TrainingParticipant participant = new TrainingParticipant();
    participant.setTrainingParticipantId(2L);
    when(participantMapper.selectList(any())).thenReturn(List.of(participant));
    TrainingParticipantContentResult row = new TrainingParticipantContentResult();
    row.setPlanContentId(11L);
    row.setResultStatus("COMPLETED");
    when(resultMapper.selectOne(any())).thenReturn(row);
    when(planMapper.updateById(any(TrainingPlan.class))).thenReturn(1);

    service.end(1L, 0);

    verify(resultSyncService).synchronize(1L);
    verify(resultSyncService).freezePlan(1L);
    ArgumentCaptor<TrainingPlan> captor = ArgumentCaptor.forClass(TrainingPlan.class);
    verify(planMapper).updateById(captor.capture());
    assertThat(captor.getValue().getPublishStatus()).isEqualTo("ENDED");
  }

  @Test
  @DisplayName("cancel：保留事实且不把人员标为 PASSED")
  void cancel_marksIncompleteNotPassed() {
    TrainingPlan plan = new TrainingPlan();
    plan.setTrainingPlanId(1L);
    plan.setPublishStatus("RUNNING");
    plan.setVersion(0);
    when(planMapper.selectById(1L)).thenReturn(plan);
    when(planMapper.updateById(any(TrainingPlan.class))).thenReturn(1);
    TrainingParticipant participant = new TrainingParticipant();
    participant.setTrainingParticipantId(2L);
    participant.setResultStatus("PASSED");
    when(participantMapper.selectList(any())).thenReturn(List.of(participant));

    service.cancel(1L, 0);

    ArgumentCaptor<TrainingParticipant> captor = ArgumentCaptor.forClass(TrainingParticipant.class);
    verify(participantMapper).updateById(captor.capture());
    assertThat(captor.getValue().getResultStatus()).isEqualTo("INCOMPLETE");
    ArgumentCaptor<TrainingPlan> planCaptor = ArgumentCaptor.forClass(TrainingPlan.class);
    verify(planMapper).updateById(planCaptor.capture());
    assertThat(planCaptor.getValue().getPublishStatus()).isEqualTo("CANCELLED");
  }

  private void publishedPlan() {
    TrainingPlan plan = new TrainingPlan();
    plan.setTrainingPlanId(1L);
    plan.setPublishStatus("PUBLISHED");
    plan.setVersion(0);
    when(planMapper.selectById(1L)).thenReturn(plan);
  }

  private void runningPlan() {
    TrainingPlan plan = new TrainingPlan();
    plan.setTrainingPlanId(1L);
    plan.setPublishStatus("RUNNING");
    plan.setVersion(0);
    when(planMapper.selectById(1L)).thenReturn(plan);
  }

  private TrainingPlanContent requiredExam() {
    TrainingPlanStage stage = new TrainingPlanStage();
    stage.setPlanStageId(5L);
    when(stageMapper.selectList(any())).thenReturn(List.of(stage));
    TrainingPlanContent exam = new TrainingPlanContent();
    exam.setPlanContentId(11L);
    exam.setPlanStageId(5L);
    exam.setContentType("EXAM");
    exam.setIsRequired(1);
    when(contentMapper.selectList(any())).thenReturn(List.of(exam));
    return exam;
  }

  private void requiredExamWithoutLink() {
    requiredExam();
    when(executionLinkMapper.selectOne(any())).thenReturn(null);
  }
}

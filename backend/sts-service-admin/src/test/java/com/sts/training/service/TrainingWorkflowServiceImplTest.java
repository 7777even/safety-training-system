package com.sts.training.service;

import com.sts.admin.entity.SysFileInfo;
import com.sts.admin.entity.SysUser;
import com.sts.admin.mapper.SysFileInfoMapper;
import com.sts.admin.mapper.SysUserMapper;
import com.sts.admin.storage.StorageService;
import com.sts.common.exception.BusinessException;
import com.sts.exam.vo.ExamAttemptVO;
import com.sts.training.dto.TrainingExamAttemptStartRequest;
import com.sts.training.entity.*;
import com.sts.training.mapper.*;
import com.sts.training.port.TrainingAssessmentExecutionPort;
import com.sts.training.port.TrainingCourseExecutionPort;
import com.sts.training.vo.TrainingLearnerExamNoticeVO;
import com.sts.training.vo.TrainingParticipantResultsVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TrainingWorkflowServiceImplTest {

  private TrainingPlanMapper planMapper;
  private TrainingParticipantMapper participantMapper;
  private TrainingPlanStageMapper stageMapper;
  private TrainingPlanContentMapper contentMapper;
  private TrainingExecutionLinkMapper executionLinkMapper;
  private TrainingOfflineSessionMapper offlineSessionMapper;
  private TrainingOfflineScoreMapper offlineScoreMapper;
  private TrainingTypeMapper typeMapper;
  private SysUserMapper userMapper;
  private SysFileInfoMapper fileInfoMapper;
  private StorageService storageService;
  private TrainingAssessmentExecutionPort assessmentPort;
  private TrainingCourseExecutionPort coursePort;
  private TrainingExecutionService trainingExecutionService;
  private TrainingResultSyncService resultSyncService;
  private TrainingWorkflowServiceImpl service;
  private TrainingPlanApprovalService approvalService;

  @Test
  void getLearnerExamNotice_readsExamSnapshotWithoutStartingAttempt() {
    TrainingParticipant participant = participant(2L, 1L, 7L);
    when(participantMapper.selectById(2L)).thenReturn(participant);
    when(planMapper.selectById(1L)).thenReturn(plan(1L, "RUNNING", "进行中"));
    TrainingPlanContent content = new TrainingPlanContent();
    content.setPlanContentId(3L);
    content.setPlanStageId(4L);
    content.setContentType("EXAM");
    when(contentMapper.selectById(3L)).thenReturn(content);
    TrainingPlanStage stage = new TrainingPlanStage();
    stage.setTrainingPlanId(1L);
    when(stageMapper.selectById(4L)).thenReturn(stage);
    TrainingExecutionLink link = new TrainingExecutionLink();
    link.setExecutionId(777L);
    link.setExecutionType("EXAM");
    when(executionLinkMapper.selectOne(any())).thenReturn(link);
    TrainingLearnerExamNoticeVO notice = new TrainingLearnerExamNoticeVO();
    notice.setExamId(88L);
    notice.setExamName("高处作业理论考试");
    when(assessmentPort.getLearnerExamNotice(777L)).thenReturn(notice);

    TrainingLearnerExamNoticeVO result = service.getLearnerExamNotice(2L, 3L, 7L);

    assertThat(result.getExamId()).isEqualTo(88L);
    verify(assessmentPort).getLearnerExamNotice(777L);
    verify(assessmentPort, never()).startAttempt(any(), any(), any(), any(), any(), any(), any(), any());
  }

  @BeforeEach
  void setUp() {
    planMapper = mock(TrainingPlanMapper.class);
    participantMapper = mock(TrainingParticipantMapper.class);
    stageMapper = mock(TrainingPlanStageMapper.class);
    contentMapper = mock(TrainingPlanContentMapper.class);
    executionLinkMapper = mock(TrainingExecutionLinkMapper.class);
    offlineSessionMapper = mock(TrainingOfflineSessionMapper.class);
    offlineScoreMapper = mock(TrainingOfflineScoreMapper.class);
    typeMapper = mock(TrainingTypeMapper.class);
    userMapper = mock(SysUserMapper.class);
    fileInfoMapper = mock(SysFileInfoMapper.class);
    storageService = mock(StorageService.class);
    assessmentPort = mock(TrainingAssessmentExecutionPort.class);
    coursePort = mock(TrainingCourseExecutionPort.class);
    trainingExecutionService = mock(TrainingExecutionService.class);
    resultSyncService = mock(TrainingResultSyncService.class);
    approvalService = mock(TrainingPlanApprovalService.class);
    when(approvalService.lockForEdit(any())).thenAnswer(inv -> planMapper.selectById(inv.getArgument(0)));
    when(planMapper.updateById(any(TrainingPlan.class))).thenReturn(1);
    service = createService(resultSyncService);
  }

  private TrainingWorkflowServiceImpl createService(TrainingResultSyncService resultSync) {
    return new TrainingWorkflowServiceImpl(
      planMapper, participantMapper, stageMapper,
      contentMapper, executionLinkMapper,
      offlineSessionMapper, offlineScoreMapper,
      mock(TrainingRewardPenaltyMapper.class),
      coursePort, assessmentPort,
      trainingExecutionService, resultSync,
      typeMapper, fileInfoMapper, storageService, userMapper,
      mock(TrainingAttachmentMapper.class), mock(TrainingAttachmentService.class), approvalService);
  }

  @Test void removePendingParticipantIsRejectedBeforeWrite() {
    when(approvalService.lockForEdit(1L)).thenThrow(new BusinessException(org.springframework.http.HttpStatus.CONFLICT,8331,"待审批计划禁止编辑"));
    assertThatThrownBy(()->service.removeParticipant(1L,2L)).hasMessageContaining("待审批");
    verify(participantMapper,never()).update(isNull(),any());
    verify(planMapper,never()).updateById(any(TrainingPlan.class));
  }

  @Test
  void removeParticipant_assignedMemberWithoutFacts_marksDeleted() {
    TrainingPlan plan = new TrainingPlan();
    plan.setTrainingPlanId(1L);
    plan.setPublishStatus("DRAFT");
    TrainingParticipant participant = new TrainingParticipant();
    participant.setTrainingParticipantId(2L);
    participant.setTrainingPlanId(1L);
    participant.setJoinSource("ASSIGN");
    when(planMapper.selectById(1L)).thenReturn(plan);
    when(participantMapper.selectById(2L)).thenReturn(participant);

    service.removeParticipant(1L, 2L);

    verify(participantMapper).update(isNull(), any());
    verify(planMapper).updateById(plan);
  }

  @Test
  void removeParticipant_rejectsParticipantFromAnotherPlan() {
    TrainingPlan plan = new TrainingPlan();
    plan.setTrainingPlanId(1L);
    plan.setPublishStatus("DRAFT");
    TrainingParticipant participant = new TrainingParticipant();
    participant.setTrainingParticipantId(2L);
    participant.setTrainingPlanId(9L);
    when(planMapper.selectById(1L)).thenReturn(plan);
    when(participantMapper.selectById(2L)).thenReturn(participant);

    assertThatThrownBy(() -> service.removeParticipant(1L, 2L))
      .isInstanceOf(BusinessException.class)
      .hasFieldOrPropertyWithValue("code", 8120);
  }

  @Test
  void getTeacherTask_returnsRosterFromParticipants() {
    TrainingOfflineSession session = new TrainingOfflineSession();
    session.setOfflineSessionId(3L);
    session.setTrainingPlanId(20L);
    session.setPlanContentId(12L);
    session.setTeacherUserId(7L);
    session.setSessionName("车间实操");
    session.setSessionStatus("IN_PROGRESS");
    session.setVersion(4);
    when(offlineSessionMapper.selectById(3L)).thenReturn(session);
    TrainingParticipant first = participant(2L, 20L, 11L);
    TrainingParticipant second = participant(3L, 20L, 12L);
    when(participantMapper.selectList(any())).thenReturn(List.of(first, second));
    TrainingOfflineScore score = new TrainingOfflineScore();
    score.setTrainingParticipantId(2L);
    score.setScore(new BigDecimal("88.00"));
    score.setMaxScore(new BigDecimal("100.00"));
    score.setScoredBy(7L);
    score.setScoredTime(java.time.LocalDateTime.of(2026, 8, 31, 11, 0));
    when(offlineScoreMapper.selectList(any())).thenReturn(List.of(score));
    TrainingParticipantResultsVO firstResults = new TrainingParticipantResultsVO();
    var item = new com.sts.training.vo.TrainingParticipantResultItemVO();
    item.setPlanContentId(12L);
    item.setResultStatus("COMPLETED");
    firstResults.setItems(List.of(item));
    when(resultSyncService.getResults(20L, 2L)).thenReturn(firstResults);
    when(resultSyncService.getResults(20L, 3L)).thenReturn(new TrainingParticipantResultsVO());
    SysUser user = new SysUser();
    user.setUserId(11L);
    user.setUserName("张三");
    when(userMapper.selectList(any())).thenReturn(List.of(user));

    var vo = service.getTeacherTask(3L, 7L);

    assertThat(vo.getParticipantCount()).isEqualTo(2);
    assertThat(vo.getScoredCount()).isEqualTo(1);
    assertThat(vo.getPendingScoreCount()).isEqualTo(1);
    assertThat(vo.getRoster()).hasSize(2);
    assertThat(vo.getRoster().getFirst().getScore()).isEqualByComparingTo("88.00");
    assertThat(vo.getRoster().getFirst().getUserName()).isEqualTo("张三");
    assertThat(vo.getRoster().get(1).getUserName()).isNull();
    assertThat(vo.getRoster().getFirst().getResultStatus()).isEqualTo("COMPLETED");
    assertThat(vo.getRoster().get(1).getScore()).isNull();
    assertThat(vo.getAvailableActions()).containsExactly("RECORD_SCORE", "COMPLETE", "CANCEL");
    assertThat(vo.getVersion()).isEqualTo(4);
  }

  @Test
  void getTeacherTask_scheduled_offersStartAndCancel() {
    TrainingOfflineSession session = new TrainingOfflineSession();
    session.setOfflineSessionId(3L);
    session.setTrainingPlanId(20L);
    session.setPlanContentId(12L);
    session.setTeacherUserId(7L);
    session.setSessionStatus("SCHEDULED");
    session.setVersion(2);
    when(offlineSessionMapper.selectById(3L)).thenReturn(session);
    when(participantMapper.selectList(any())).thenReturn(List.of());
    when(offlineScoreMapper.selectList(any())).thenReturn(List.of());

    var vo = service.getTeacherTask(3L, 7L);

    assertThat(vo.getAvailableActions()).containsExactly("START", "CANCEL");
    assertThat(vo.getVersion()).isEqualTo(2);
  }

  @Test
  void getTeacherTask_rejectsDifferentTeacher() {
    TrainingOfflineSession session = new TrainingOfflineSession();
    session.setOfflineSessionId(3L);
    session.setTeacherUserId(7L);
    when(offlineSessionMapper.selectById(3L)).thenReturn(session);

    assertThatThrownBy(() -> service.getTeacherTask(3L, 8L))
      .isInstanceOf(BusinessException.class)
      .hasFieldOrPropertyWithValue("code", 8131);
  }

  @Test
  void startExam_rejectsAnotherLearnerBeforeCallingExamDomain() {
    TrainingParticipant participant = new TrainingParticipant();
    participant.setTrainingParticipantId(2L);
    participant.setTrainingPlanId(1L);
    participant.setUserId(7L);
    when(participantMapper.selectById(2L)).thenReturn(participant);

    assertThatThrownBy(() -> service.startExam(
      2L, 3L, new TrainingExamAttemptStartRequest(), 8L))
      .isInstanceOf(BusinessException.class)
      .hasFieldOrPropertyWithValue("code", 8150);

    verifyNoInteractions(assessmentPort);
  }

  @Test
  void provision_rejectsContentFromAnotherPlan() {
    when(trainingExecutionService.provision(1L, 3L))
      .thenThrow(new BusinessException(org.springframework.http.HttpStatus.BAD_REQUEST, 8145,
                                       "计划内容不属于指定培训计划"));

    assertThatThrownBy(() -> service.provision(1L, 3L))
      .isInstanceOf(BusinessException.class)
      .hasFieldOrPropertyWithValue("code", 8145);

    verify(trainingExecutionService).provision(1L, 3L);
    verifyNoInteractions(executionLinkMapper);
  }

  @Test
  void synchronizeResults_delegatesToProjectionSync() {
    TrainingParticipantResultsVO vo = new TrainingParticipantResultsVO();
    vo.setCourseScore(new BigDecimal("80"));
    TrainingResultSyncService resultSync = mock(TrainingResultSyncService.class);
    service = createService(resultSync);
    when(resultSync.synchronize(1L)).thenReturn(List.of(vo));

    var results = service.synchronizeResults(1L);

    assertThat(results.getFirst().getCourseScore()).isEqualByComparingTo("80");
    verify(resultSync).synchronize(1L);
  }

  @Test
  void synchronizeResults_repairsUnfinalizedTrainingCourseScoresBeforeProjectionSync() {
    TrainingParticipant participant = participant(2L, 1L, 7L);
    when(participantMapper.selectList(any())).thenReturn(List.of(participant));
    TrainingPlanStage stage = new TrainingPlanStage();
    stage.setPlanStageId(4L);
    when(stageMapper.selectList(any())).thenReturn(List.of(stage));
    TrainingPlanContent content = new TrainingPlanContent();
    content.setPlanContentId(3L);
    content.setPlanStageId(4L);
    content.setContentType("COURSE");
    when(contentMapper.selectList(any())).thenReturn(List.of(content));
    when(coursePort.findOfferingIdByPlanContentId(3L)).thenReturn(101L);
    when(resultSyncService.synchronize(1L)).thenReturn(List.of());

    service.synchronizeResults(1L);

    verify(coursePort).reconcileTrainingFinalScore(101L, 2L);
    verify(resultSyncService).synchronize(1L);
  }

  @Test
  void startExam_usesAssessmentExecutionIdNotSourceExamId() {
    TrainingParticipant participant = new TrainingParticipant();
    participant.setTrainingParticipantId(2L);
    participant.setTrainingPlanId(1L);
    participant.setUserId(7L);
    when(participantMapper.selectById(2L)).thenReturn(participant);
    TrainingPlan plan = new TrainingPlan();
    plan.setTrainingPlanId(1L);
    plan.setPublishStatus("RUNNING");
    when(planMapper.selectById(1L)).thenReturn(plan);
    TrainingPlanContent content = new TrainingPlanContent();
    content.setPlanContentId(3L);
    content.setPlanStageId(4L);
    content.setContentType("EXAM");
    when(contentMapper.selectById(3L)).thenReturn(content);
    TrainingPlanStage stage = new TrainingPlanStage();
    stage.setPlanStageId(4L);
    stage.setTrainingPlanId(1L);
    when(stageMapper.selectById(4L)).thenReturn(stage);
    com.sts.training.entity.TrainingExecutionLink link = new com.sts.training.entity.TrainingExecutionLink();
    link.setExecutionId(777L);
    link.setExecutionType("EXAM");
    when(executionLinkMapper.selectOne(any())).thenReturn(link);
    TrainingExamAttemptStartRequest request = new TrainingExamAttemptStartRequest();
    request.setClientAttemptToken("tok");
    ExamAttemptVO started = new ExamAttemptVO();
    started.setExamAttemptId(9001L);
    when(assessmentPort.startAttempt(eq(777L), eq(2L), eq(7L), eq("tok"), any(), any(), any(), any()))
      .thenReturn(started);

    service.startExam(2L, 3L, request, 7L);

    verify(assessmentPort).startAttempt(eq(777L), eq(2L), eq(7L), eq("tok"), any(), any(), any(), any());
  }

  @Test
  void enter_rejectsAnotherLearner() {
    TrainingParticipant participant = new TrainingParticipant();
    participant.setTrainingParticipantId(2L);
    participant.setTrainingPlanId(1L);
    participant.setUserId(7L);
    when(participantMapper.selectById(2L)).thenReturn(participant);

    assertThatThrownBy(() -> service.enter(2L, 3L, new com.sts.training.dto.TrainingLearnerEnterRequest(), 8L))
      .isInstanceOf(BusinessException.class)
      .hasFieldOrPropertyWithValue("code", 8150);
    verifyNoInteractions(assessmentPort);
  }

  @Test
  void enter_rejectsDraftPlan() {
    TrainingParticipant participant = new TrainingParticipant();
    participant.setTrainingParticipantId(2L);
    participant.setTrainingPlanId(1L);
    participant.setUserId(7L);
    when(participantMapper.selectById(2L)).thenReturn(participant);
    TrainingPlan plan = new TrainingPlan();
    plan.setTrainingPlanId(1L);
    plan.setPublishStatus("DRAFT");
    when(planMapper.selectById(1L)).thenReturn(plan);

    assertThatThrownBy(() -> service.enter(2L, 3L, new com.sts.training.dto.TrainingLearnerEnterRequest(), 7L))
      .isInstanceOf(BusinessException.class)
      .hasFieldOrPropertyWithValue("code", 8154);
  }

  @Test
  void listLearnerTasks_returnsOnlyRunningAndEndedPlans() {
    when(participantMapper.selectList(any())).thenReturn(List.of(
      participant(1L, 10L, 7L),
      participant(2L, 20L, 7L),
      participant(3L, 30L, 7L),
      participant(4L, 40L, 7L),
      participant(5L, 50L, 7L),
      participant(6L, 60L, 7L)
    ));
    when(planMapper.selectById(10L)).thenReturn(plan(10L, "PUBLISHED", "已发布"));
    when(planMapper.selectById(20L)).thenReturn(plan(20L, "RUNNING", "进行中", 5L));
    when(planMapper.selectById(30L)).thenReturn(plan(30L, "ENDED", "已结束", 6L));
    when(planMapper.selectById(40L)).thenReturn(plan(40L, "DRAFT", "草稿"));
    when(planMapper.selectById(50L)).thenReturn(plan(50L, "CANCELLED", "已取消"));
    when(planMapper.selectById(60L)).thenReturn(plan(60L, "ARCHIVED", "已归档"));
    TrainingType typeWithCover = new TrainingType();
    typeWithCover.setTrainingTypeId(5L);
    typeWithCover.setCoverFileId(900L);
    typeWithCover.setTypeName("高处作业");
    TrainingType typeWithoutCover = new TrainingType();
    typeWithoutCover.setTrainingTypeId(6L);
    typeWithoutCover.setTypeName("安全法规");
    when(typeMapper.selectById(5L)).thenReturn(typeWithCover);
    when(typeMapper.selectById(6L)).thenReturn(typeWithoutCover);
    // 批量查名称：listLearnerTasks 收集可见任务的 typeId 集合后一次性 selectBatchIds 回填。
    when(typeMapper.selectBatchIds(anyCollection())).thenReturn(List.of(typeWithCover, typeWithoutCover));
    TrainingParticipantResultsVO runningResults = new TrainingParticipantResultsVO();
    runningResults.setResultStatus("INCOMPLETE");
    TrainingParticipantResultsVO endedResults = new TrainingParticipantResultsVO();
    endedResults.setResultStatus("PASSED");
    when(resultSyncService.getResults(20L, 2L)).thenReturn(runningResults);
    when(resultSyncService.getResults(30L, 3L)).thenReturn(endedResults);

    var tasks = service.listLearnerTasks(7L);

    assertThat(tasks).extracting(com.sts.training.vo.TrainingLearnerTaskVO::getTrainingPlanId)
      .containsExactly(20L, 30L);
    assertThat(tasks).extracting(com.sts.training.vo.TrainingLearnerTaskVO::getPublishStatus)
      .containsExactly("RUNNING", "ENDED");
    assertThat(tasks.getFirst().getTrainingTypeId()).isEqualTo(5L);
    assertThat(tasks.getFirst().getCoverUrl()).isEqualTo("/api/training/learner/plans/20/cover");
    assertThat(tasks.get(1).getTrainingTypeId()).isEqualTo(6L);
    assertThat(tasks.get(1).getCoverUrl()).isNull();
    // 新增字段：按 typeId 批量回填的培训类型名称。
    assertThat(tasks.getFirst().getTrainingTypeName()).isEqualTo("高处作业");
    assertThat(tasks.get(1).getTrainingTypeName()).isEqualTo("安全法规");
    // 批量查询：可见任务的两个 typeId（5、6）只做一次 selectBatchIds 回填名称。
    verify(typeMapper).selectBatchIds(anyCollection());
  }

  @Test
  void listLearnerTasks_typeNameMissingOrStoppedFallsBackToNull() {
    when(participantMapper.selectList(any())).thenReturn(List.of(
      participant(2L, 20L, 7L),
      participant(3L, 30L, 7L)));
    when(planMapper.selectById(20L)).thenReturn(plan(20L, "RUNNING", "进行中", 5L));
    when(planMapper.selectById(30L)).thenReturn(plan(30L, "ENDED", "已结束", 99L));
    TrainingType existing = new TrainingType();
    existing.setTrainingTypeId(5L);
    existing.setTypeName("高处作业");
    // 99L 类型不存在：selectBatchIds 只返回 5L 一条，99L 缺失 → 名称回填为 null。
    when(typeMapper.selectBatchIds(anyCollection())).thenReturn(List.of(existing));
    when(resultSyncService.getResults(20L, 2L)).thenReturn(new TrainingParticipantResultsVO());
    when(resultSyncService.getResults(30L, 3L)).thenReturn(new TrainingParticipantResultsVO());

    var tasks = service.listLearnerTasks(7L);

    assertThat(tasks.getFirst().getTrainingTypeName()).isEqualTo("高处作业");
    assertThat(tasks.get(1).getTrainingTypeId()).isEqualTo(99L);
    assertThat(tasks.get(1).getTrainingTypeName()).isNull();
  }

  @Test
  void listLearnerTasks_usesRealtimeResultStatusForRunningPlan() {
    TrainingParticipant participant = participant(2L, 20L, 7L);
    participant.setResultStatus(null);
    when(participantMapper.selectList(any())).thenReturn(List.of(participant));
    when(planMapper.selectById(20L)).thenReturn(plan(20L, "RUNNING", "进行中"));
    TrainingParticipantResultsVO realtimeResults = new TrainingParticipantResultsVO();
    realtimeResults.setResultStatus("INCOMPLETE");
    when(resultSyncService.getResults(20L, 2L)).thenReturn(realtimeResults);

    var tasks = service.listLearnerTasks(7L);

    assertThat(tasks.getFirst().getResultStatus()).isEqualTo("INCOMPLETE");
    verify(resultSyncService).getResults(20L, 2L);
  }

  @Test
  void openLearnerPlanCover_returnsTypeCoverForVisibleParticipant() {
    when(planMapper.selectById(20L)).thenReturn(plan(20L, "RUNNING", "进行中", 5L));
    when(participantMapper.selectOne(any())).thenReturn(participant(2L, 20L, 7L));
    TrainingType type = new TrainingType();
    type.setTrainingTypeId(5L);
    type.setCoverFileId(900L);
    when(typeMapper.selectById(5L)).thenReturn(type);
    SysFileInfo file = new SysFileInfo();
    file.setFileInfoId(900L);
    file.setFileName("type-cover.png");
    file.setObjectName("DEMO/training-type/cover.png");
    file.setFileSize(4L);
    file.setFileSuffix("png");
    file.setVersion(1);
    when(fileInfoMapper.selectById(900L)).thenReturn(file);
    org.springframework.core.io.ByteArrayResource resource =
      new org.springframework.core.io.ByteArrayResource("png!".getBytes());
    when(storageService.download("DEMO/training-type/cover.png")).thenReturn(resource);

    TrainingCoverFile cover = service.openLearnerPlanCover(20L, 7L);

    assertThat(cover.fileName()).isEqualTo("type-cover.png");
    assertThat(cover.contentType()).isEqualTo("image/png");
    assertThat(cover.etag()).isEqualTo("\"900-1-4\"");
    assertThat(cover.resource()).isSameAs(resource);
  }

  @Test
  void openLearnerPlanCover_hidesInvisiblePlanAs8155() {
    when(planMapper.selectById(20L)).thenReturn(plan(20L, "PUBLISHED", "已发布", 5L));

    assertThatThrownBy(() -> service.openLearnerPlanCover(20L, 7L))
      .isInstanceOf(BusinessException.class)
      .hasFieldOrPropertyWithValue("code", 8155);
    verifyNoInteractions(storageService);
  }

  @Test
  void openLearnerPlanCover_missingCover_throws8164() {
    when(planMapper.selectById(20L)).thenReturn(plan(20L, "RUNNING", "进行中", 5L));
    when(participantMapper.selectOne(any())).thenReturn(participant(2L, 20L, 7L));
    TrainingType type = new TrainingType();
    type.setTrainingTypeId(5L);
    when(typeMapper.selectById(5L)).thenReturn(type);

    assertThatThrownBy(() -> service.openLearnerPlanCover(20L, 7L))
      .isInstanceOfSatisfying(BusinessException.class, error -> {
        assertThat(error.getStatus()).isEqualTo(org.springframework.http.HttpStatus.NOT_FOUND);
        assertThat(error.getCode()).isEqualTo(8164);
      });
    verifyNoInteractions(storageService);
  }

  @Test
  void getLearnerParticipant_allowsEndedPlan() {
    when(participantMapper.selectById(2L)).thenReturn(participant(2L, 1L, 7L));
    when(planMapper.selectById(1L)).thenReturn(plan(1L, "ENDED", "已结束"));
    when(stageMapper.selectList(any())).thenReturn(List.of());
    TrainingParticipantResultsVO results = new TrainingParticipantResultsVO();
    results.setItems(List.of());
    when(resultSyncService.getResults(1L, 2L)).thenReturn(results);

    var detail = service.getLearnerParticipant(2L, 7L);

    assertThat(detail.getParticipant()).isNotNull();
    assertThat(detail.getParticipant().getTrainingParticipantId()).isEqualTo(2L);
    assertThat(detail.getPlan()).isNotNull();
    assertThat(detail.getPlan().getTrainingPlanId()).isEqualTo(1L);
    assertThat(detail.getPlan().getPublishStatus()).isEqualTo("ENDED");
  }

  @Test
  void getLearnerParticipant_rejectsInvisiblePlan() {
    when(participantMapper.selectById(2L)).thenReturn(participant(2L, 1L, 7L));
    when(planMapper.selectById(1L)).thenReturn(plan(1L, "DRAFT", "草稿"));

    assertThatThrownBy(() -> service.getLearnerParticipant(2L, 7L))
      .isInstanceOf(BusinessException.class)
      .hasFieldOrPropertyWithValue("code", 8155)
      .hasFieldOrPropertyWithValue("status", org.springframework.http.HttpStatus.NOT_FOUND);
    verifyNoInteractions(resultSyncService);
  }

  @Test
  void getLearnerParticipant_rejectsPublishedPlanUntilStarted() {
    when(participantMapper.selectById(2L)).thenReturn(participant(2L, 1L, 7L));
    when(planMapper.selectById(1L)).thenReturn(plan(1L, "PUBLISHED", "已发布"));

    assertThatThrownBy(() -> service.getLearnerParticipant(2L, 7L))
      .isInstanceOf(BusinessException.class)
      .hasFieldOrPropertyWithValue("code", 8155)
      .hasFieldOrPropertyWithValue("status", org.springframework.http.HttpStatus.NOT_FOUND);
    verifyNoInteractions(resultSyncService);
  }

  @Test
  void getLearnerParticipant_rejectsCancelledPlan() {
    when(participantMapper.selectById(2L)).thenReturn(participant(2L, 1L, 7L));
    when(planMapper.selectById(1L)).thenReturn(plan(1L, "CANCELLED", "已取消"));

    assertThatThrownBy(() -> service.getLearnerParticipant(2L, 7L))
      .isInstanceOf(BusinessException.class)
      .hasFieldOrPropertyWithValue("code", 8155);
  }

  @Test
  void getLearnerParticipant_rejectsOtherUserBeforePlanVisibility() {
    when(participantMapper.selectById(2L)).thenReturn(participant(2L, 1L, 9L));

    assertThatThrownBy(() -> service.getLearnerParticipant(2L, 7L))
      .isInstanceOf(BusinessException.class)
      .hasFieldOrPropertyWithValue("code", 8150);
    verify(planMapper, never()).selectById(any());
  }

  @Test
  void enter_rejectsEndedPlan() {
    TrainingParticipant participant = participant(2L, 1L, 7L);
    when(participantMapper.selectById(2L)).thenReturn(participant);
    when(planMapper.selectById(1L)).thenReturn(plan(1L, "ENDED", "已结束"));

    assertThatThrownBy(() -> service.enter(2L, 3L, new com.sts.training.dto.TrainingLearnerEnterRequest(), 7L))
      .isInstanceOf(BusinessException.class)
      .hasFieldOrPropertyWithValue("code", 8154);
  }

  @Test
  void enter_rejectsPublishedPlanUntilStarted() {
    TrainingParticipant participant = participant(2L, 1L, 7L);
    when(participantMapper.selectById(2L)).thenReturn(participant);
    when(planMapper.selectById(1L)).thenReturn(plan(1L, "PUBLISHED", "已发布"));

    assertThatThrownBy(() -> service.enter(2L, 3L, new com.sts.training.dto.TrainingLearnerEnterRequest(), 7L))
      .isInstanceOf(BusinessException.class)
      .hasFieldOrPropertyWithValue("code", 8154);
  }

  @Test
  void getLearnerOfflineSession_returnsOwnScoreAndProjection() {
    TrainingOfflineSession session = offlineSession(301L, 20L, 12L);
    when(offlineSessionMapper.selectById(301L)).thenReturn(session);
    when(planMapper.selectById(20L)).thenReturn(plan(20L, "RUNNING", "进行中"));
    when(participantMapper.selectOne(any())).thenReturn(participant(2L, 20L, 7L));
    TrainingOfflineScore score = new TrainingOfflineScore();
    score.setScore(new BigDecimal("88.00"));
    score.setScoredBy(9L);
    score.setScoredTime(java.time.LocalDateTime.of(2026, 8, 31, 11, 0));
    when(offlineScoreMapper.selectOne(any())).thenReturn(score);
    TrainingParticipantResultsVO results = new TrainingParticipantResultsVO();
    var item = new com.sts.training.vo.TrainingParticipantResultItemVO();
    item.setPlanContentId(12L);
    item.setResultStatus("COMPLETED");
    item.setEffectiveDurationSeconds(7200);
    results.setItems(List.of(item));
    when(resultSyncService.getResults(20L, 2L)).thenReturn(results);

    var vo = service.getLearnerOfflineSession(301L, 7L);

    assertThat(vo.getOfflineSessionId()).isEqualTo(301L);
    assertThat(vo.getTrainingParticipantId()).isEqualTo(2L);
    assertThat(vo.getPlanContentId()).isEqualTo(12L);
    assertThat(vo.getSessionName()).isEqualTo("车间实操");
    assertThat(vo.getScore()).isEqualByComparingTo("88.00");
    assertThat(vo.getResultStatus()).isEqualTo("COMPLETED");
    assertThat(vo.getEffectiveDurationSeconds()).isEqualTo(7200);
  }

  @Test
  void getLearnerOfflineSession_allowsEndedPlan() {
    when(offlineSessionMapper.selectById(301L)).thenReturn(offlineSession(301L, 20L, 12L));
    when(planMapper.selectById(20L)).thenReturn(plan(20L, "ENDED", "已结束"));
    when(participantMapper.selectOne(any())).thenReturn(participant(2L, 20L, 7L));
    when(offlineScoreMapper.selectOne(any())).thenReturn(null);
    TrainingParticipantResultsVO results = new TrainingParticipantResultsVO();
    results.setItems(List.of());
    when(resultSyncService.getResults(20L, 2L)).thenReturn(results);

    var vo = service.getLearnerOfflineSession(301L, 7L);

    assertThat(vo.getResultStatus()).isEqualTo("PENDING");
    assertThat(vo.getEffectiveDurationSeconds()).isZero();
    assertThat(vo.getScore()).isNull();
  }

  @Test
  void getLearnerOfflineSession_hidesMissingSessionAs8155() {
    when(offlineSessionMapper.selectById(301L)).thenReturn(null);

    assertThatThrownBy(() -> service.getLearnerOfflineSession(301L, 7L))
      .isInstanceOf(BusinessException.class)
      .hasFieldOrPropertyWithValue("status", org.springframework.http.HttpStatus.NOT_FOUND)
      .hasFieldOrPropertyWithValue("code", 8155);
  }

  @Test
  void getLearnerOfflineSession_hidesNonParticipantAs8155() {
    when(offlineSessionMapper.selectById(301L)).thenReturn(offlineSession(301L, 20L, 12L));
    when(planMapper.selectById(20L)).thenReturn(plan(20L, "RUNNING", "进行中"));
    when(participantMapper.selectOne(any())).thenReturn(null);

    assertThatThrownBy(() -> service.getLearnerOfflineSession(301L, 7L))
      .isInstanceOf(BusinessException.class)
      .hasFieldOrPropertyWithValue("code", 8155);
    verifyNoInteractions(resultSyncService);
  }

  @Test
  void getLearnerOfflineSession_hidesPublishedPlanAs8155() {
    when(offlineSessionMapper.selectById(301L)).thenReturn(offlineSession(301L, 20L, 12L));
    when(planMapper.selectById(20L)).thenReturn(plan(20L, "PUBLISHED", "已发布"));

    assertThatThrownBy(() -> service.getLearnerOfflineSession(301L, 7L))
      .isInstanceOf(BusinessException.class)
      .hasFieldOrPropertyWithValue("code", 8155);
    verify(participantMapper, never()).selectOne(any());
  }

  @Test
  void getEndedResult_rejectsNonEndedPlan() {
    when(planMapper.selectById(1L)).thenReturn(plan(1L, "RUNNING", "进行中"));
    assertThatThrownBy(() -> service.getEndedResult(1L))
      .isInstanceOf(BusinessException.class)
      .hasFieldOrPropertyWithValue("code", 8123);
  }

  @Test
  void getEndedResult_summarizesPassedParticipants() {
    TrainingPlan ended = plan(1L, "ENDED", "已结束");
    ended.setPlanCode("P-END");
    when(planMapper.selectById(1L)).thenReturn(ended);
    TrainingParticipant passed = participant(2L, 1L, 9L);
    passed.setResultStatus("PASSED");
    passed.setCompositeScore(new BigDecimal("85"));
    when(participantMapper.selectList(any())).thenReturn(List.of(passed));
    TrainingParticipantResultsVO results = new TrainingParticipantResultsVO();
    results.setTrainingParticipantId(2L);
    when(resultSyncService.getResults(1L, 2L)).thenReturn(results);

    var vo = service.getEndedResult(1L);
    assertThat(vo.getPlanCode()).isEqualTo("P-END");
    assertThat(vo.getParticipantCount()).isEqualTo(1L);
    assertThat(vo.getPassedCount()).isEqualTo(1L);
    assertThat(vo.getFailedCount()).isEqualTo(0L);
    assertThat(vo.getIncompleteCount()).isEqualTo(0L);
    assertThat(vo.getParticipants()).hasSize(1);
    assertThat(vo.getAverageCompositeScore()).isEqualByComparingTo("85.00");
  }

  private static TrainingOfflineSession offlineSession(Long sessionId, Long planId, Long planContentId) {
    TrainingOfflineSession session = new TrainingOfflineSession();
    session.setOfflineSessionId(sessionId);
    session.setTrainingPlanId(planId);
    session.setPlanContentId(planContentId);
    session.setSessionName("车间实操");
    session.setLocation("3号车间");
    session.setSessionStatus("IN_PROGRESS");
    session.setStartTime(java.time.LocalDateTime.of(2026, 8, 31, 9, 0));
    session.setEndTime(java.time.LocalDateTime.of(2026, 8, 31, 11, 0));
    return session;
  }

  private static TrainingParticipant participant(Long participantId, Long planId, Long userId) {
    TrainingParticipant participant = new TrainingParticipant();
    participant.setTrainingParticipantId(participantId);
    participant.setTrainingPlanId(planId);
    participant.setUserId(userId);
    participant.setParticipantStatus("PENDING");
    return participant;
  }

  private static TrainingPlan plan(Long planId, String publishStatus, String planName) {
    return plan(planId, publishStatus, planName, null);
  }

  private static TrainingPlan plan(Long planId, String publishStatus, String planName, Long trainingTypeId) {
    TrainingPlan plan = new TrainingPlan();
    plan.setTrainingPlanId(planId);
    plan.setPublishStatus(publishStatus);
    plan.setPlanName(planName);
    plan.setTrainingTypeId(trainingTypeId);
    return plan;
  }
}

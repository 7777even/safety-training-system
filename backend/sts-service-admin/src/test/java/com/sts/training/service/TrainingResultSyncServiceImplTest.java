package com.sts.training.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sts.certificate.service.CertificateAutoIssueService;
import com.sts.common.exception.BusinessException;
import com.sts.training.entity.*;
import com.sts.training.mapper.*;
import com.sts.training.port.TrainingAssessmentExecutionPort;
import com.sts.training.port.TrainingAssessmentResult;
import com.sts.training.port.TrainingCourseExecutionPort;
import com.sts.training.port.TrainingCourseResult;
import com.sts.training.vo.TrainingParticipantResultsVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TrainingResultSyncServiceImplTest {

  private TrainingPlanMapper planMapper;
  private TrainingParticipantMapper participantMapper;
  private TrainingPlanStageMapper stageMapper;
  private TrainingPlanContentMapper contentMapper;
  private TrainingExecutionLinkMapper executionLinkMapper;
  private TrainingOfflineSessionMapper offlineSessionMapper;
  private TrainingOfflineScoreMapper offlineScoreMapper;
  private TrainingParticipantContentResultMapper resultMapper;
  private TrainingCourseExecutionPort coursePort;
  private TrainingAssessmentExecutionPort assessmentPort;
  private TrainingResultSyncServiceImpl service;
  private final AtomicLong ids = new AtomicLong(1);

  @BeforeEach
  void setUp() {
    planMapper = mock(TrainingPlanMapper.class);
    participantMapper = mock(TrainingParticipantMapper.class);
    stageMapper = mock(TrainingPlanStageMapper.class);
    contentMapper = mock(TrainingPlanContentMapper.class);
    executionLinkMapper = mock(TrainingExecutionLinkMapper.class);
    offlineSessionMapper = mock(TrainingOfflineSessionMapper.class);
    offlineScoreMapper = mock(TrainingOfflineScoreMapper.class);
    resultMapper = mock(TrainingParticipantContentResultMapper.class);
    coursePort = mock(TrainingCourseExecutionPort.class);
    assessmentPort = mock(TrainingAssessmentExecutionPort.class);
    ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    service = new TrainingResultSyncServiceImpl(
      planMapper, participantMapper, stageMapper, contentMapper, executionLinkMapper,
      offlineSessionMapper, offlineScoreMapper, resultMapper,
      coursePort, assessmentPort, new TrainingContentConfigCodec(objectMapper), objectMapper,
      mock(CompositeScoreService.class), mock(CertificateAutoIssueService.class));
  }

  @Test
  @DisplayName("草稿无必修内容不得统计为已完成")
  void draftWithoutContentsIsNotCompleted() {
    planAndParticipant();var draft=new TrainingPlan();draft.setTrainingPlanId(1L);draft.setPublishStatus("DRAFT");when(planMapper.selectById(1L)).thenReturn(draft);
    when(stageMapper.selectList(any())).thenReturn(List.of());
    assertThat(service.getCurrentResults(1L,2L).getResultStatus()).isEqualTo("INCOMPLETE");
    verifyNoInteractions(assessmentPort,coursePort);
    verifyNoUpdates();
  }

  @Test
  @DisplayName("统计读取实时执行事实且不写投影或参训人")
  void currentResultsReadSourceWithoutWrites() {
    planAndParticipant();var exam=content(11L,"EXAM",1);bindContent(exam);link(11L,"EXAM",501L);
    var source=new TrainingAssessmentResult();source.setSourceStatus("IN_PROGRESS");source.setSourceRecordId(91L);
    when(assessmentPort.readResult(501L,2L)).thenReturn(source);
    var result=service.getCurrentResults(1L,2L);
    assertThat(result.getItems()).hasSize(1);
    assertThat(result.getItems().get(0).getResultStatus()).isEqualTo("IN_PROGRESS");
    verify(resultMapper,never()).insert(any(TrainingParticipantContentResult.class));
    verify(resultMapper,never()).updateById(any(TrainingParticipantContentResult.class));
    verify(participantMapper,never()).updateById(any(TrainingParticipant.class));
  }

  @Test
  @DisplayName("单行刷新：交卷未发布时投影为 PENDING_PUBLISH 并重建聚合")
  void synchronizeParticipantContent_unpublishedExam_updatesProjection() {
    planAndParticipant();
    TrainingPlanContent exam = content(11L, "EXAM", 1);
    bindContent(exam);
    link(11L, "EXAM", 501L);
    TrainingAssessmentResult unpublished = new TrainingAssessmentResult();
    unpublished.setSourceRecordId(91L);
    unpublished.setSourceStatus("SUBMITTED");
    unpublished.setFinalAttemptId(77L);
    when(assessmentPort.readResult(501L, 2L)).thenReturn(unpublished);
    when(resultMapper.selectOne(any())).thenReturn(null);
    when(resultMapper.selectList(any())).thenReturn(List.of());
    when(resultMapper.insert(any(TrainingParticipantContentResult.class))).thenAnswer(inv -> {
      TrainingParticipantContentResult row = inv.getArgument(0);
      row.setTrainingParticipantContentResultId(ids.getAndIncrement());
      when(resultMapper.selectList(any())).thenReturn(List.of(copy(row)));
      return 1;
    });

    service.synchronizeParticipantContent(2L, 11L);

    ArgumentCaptor<TrainingParticipantContentResult> captor =
      ArgumentCaptor.forClass(TrainingParticipantContentResult.class);
    verify(resultMapper).insert(captor.capture());
    assertThat(captor.getValue().getResultStatus()).isEqualTo("PENDING_PUBLISH");
    assertThat(captor.getValue().getScore()).isNull();
    verify(participantMapper).updateById(argThat((TrainingParticipant p) ->
                                                   p.getOnlineHours().compareTo(BigDecimal.ZERO) == 0));
  }

  @Test
  @DisplayName("单行刷新：任一内容开始后参训人从待开始进入学习中")
  void synchronizeParticipantContent_startedContent_marksParticipantLearning() {
    planAndParticipant();
    TrainingPlanContent exam = content(11L, "EXAM", 1);
    bindContent(exam);
    link(11L, "EXAM", 501L);
    TrainingAssessmentResult answering = new TrainingAssessmentResult();
    answering.setSourceRecordId(91L);
    answering.setSourceStatus("IN_PROGRESS");
    when(assessmentPort.readResult(501L, 2L)).thenReturn(answering);
    when(resultMapper.selectOne(any())).thenReturn(null);
    when(resultMapper.selectList(any())).thenReturn(List.of());
    when(resultMapper.insert(any(TrainingParticipantContentResult.class))).thenAnswer(invocation -> {
      TrainingParticipantContentResult row = invocation.getArgument(0);
      when(resultMapper.selectList(any())).thenReturn(List.of(copy(row)));
      return 1;
    });

    service.synchronizeParticipantContent(2L, 11L);

    verify(participantMapper).updateById(argThat((TrainingParticipant participant) ->
      "LEARNING".equals(participant.getParticipantStatus())));
  }

  @Test
  @DisplayName("单行刷新：待人工批阅的培训考试投影为 PENDING_REVIEW")
  void synchronizeParticipantContent_pendingReviewExam_updatesProjection() {
    planAndParticipant();
    TrainingPlanContent exam = content(11L, "EXAM", 1);
    bindContent(exam);
    link(11L, "EXAM", 501L);
    TrainingAssessmentResult pendingReview = new TrainingAssessmentResult();
    pendingReview.setSourceRecordId(91L);
    pendingReview.setSourceStatus("PENDING_REVIEW");
    pendingReview.setFinalAttemptId(77L);
    when(assessmentPort.readResult(501L, 2L)).thenReturn(pendingReview);
    when(resultMapper.selectOne(any())).thenReturn(null);
    when(resultMapper.selectList(any())).thenReturn(List.of());
    when(resultMapper.insert(any(TrainingParticipantContentResult.class))).thenAnswer(inv -> {
      TrainingParticipantContentResult row = inv.getArgument(0);
      row.setTrainingParticipantContentResultId(ids.getAndIncrement());
      when(resultMapper.selectList(any())).thenReturn(List.of(copy(row)));
      return 1;
    });

    service.synchronizeParticipantContent(2L, 11L);

    ArgumentCaptor<TrainingParticipantContentResult> captor =
      ArgumentCaptor.forClass(TrainingParticipantContentResult.class);
    verify(resultMapper).insert(captor.capture());
    assertThat(captor.getValue().getSourceStatus()).isEqualTo("PENDING_REVIEW");
    assertThat(captor.getValue().getResultStatus()).isEqualTo("PENDING_REVIEW");
    assertThat(captor.getValue().getEffectiveDurationSeconds()).isZero();
  }

  @Test
  @DisplayName("单行刷新：已冻结参训人不更新投影")
  void synchronizeParticipantContent_frozen_skips() {
    planAndParticipant();
    TrainingParticipant frozen = archivedParticipant(v2SnapshotJson());
    when(participantMapper.selectById(2L)).thenReturn(frozen);

    service.synchronizeParticipantContent(2L, 11L);

    verify(contentMapper, never()).selectById(any());
    verify(resultMapper, never()).insert(any(TrainingParticipantContentResult.class));
    verifyNoUpdates();
  }

  @Test
  @DisplayName("单行刷新：内容不属于该计划 400/8120")
  void synchronizeParticipantContent_contentOnOtherPlan_rejects() {
    planAndParticipant();
    TrainingPlanContent exam = content(11L, "EXAM", 1);
    when(contentMapper.selectById(11L)).thenReturn(exam);
    TrainingPlanStage other = new TrainingPlanStage();
    other.setPlanStageId(5L);
    other.setTrainingPlanId(99L);
    when(stageMapper.selectById(5L)).thenReturn(other);

    assertThatThrownBy(() -> service.synchronizeParticipantContent(2L, 11L))
      .isInstanceOfSatisfying(BusinessException.class, ex -> {
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getCode()).isEqualTo(8120);
      });
    verify(resultMapper, never()).insert(any(TrainingParticipantContentResult.class));
  }

  @Test
  @DisplayName("单行刷新：字段未变不递增 resultRevision")
  void synchronizeParticipantContent_unchanged_keepsRevision() {
    planAndParticipant();
    TrainingPlanContent exam = content(11L, "EXAM", 1);
    bindContent(exam);
    link(11L, "EXAM", 501L);
    TrainingAssessmentResult unpublished = new TrainingAssessmentResult();
    unpublished.setSourceRecordId(91L);
    unpublished.setSourceStatus("SUBMITTED");
    unpublished.setFinalAttemptId(77L);
    when(assessmentPort.readResult(501L, 2L)).thenReturn(unpublished);
    TrainingParticipantContentResult existing = new TrainingParticipantContentResult();
    existing.setTrainingParticipantContentResultId(9L);
    existing.setTrainingPlanId(1L);
    existing.setTrainingParticipantId(2L);
    existing.setPlanContentId(11L);
    existing.setContentType("EXAM");
    existing.setExecutionId(501L);
    existing.setSourceRecordType("EXAM_CANDIDATE");
    existing.setSourceRecordId(91L);
    existing.setFinalAttemptId(77L);
    existing.setSourceStatus("SUBMITTED");
    existing.setResultStatus("PENDING_PUBLISH");
    existing.setEffectiveDurationSeconds(0);
    existing.setResultRevision(1);
    when(resultMapper.selectOne(any())).thenReturn(existing);
    when(resultMapper.selectList(any())).thenReturn(List.of(existing));

    service.synchronizeParticipantContent(2L, 11L);

    verify(resultMapper).updateById(argThat((TrainingParticipantContentResult row) ->
                                              Integer.valueOf(1).equals(row.getResultRevision())));
  }

  @Test
  @DisplayName("单行刷新：新一轮全主观练习应清空上一轮正确率")
  void synchronizeParticipantContent_allRecordOnlyRoundClearsPreviousAccuracy() {
    planAndParticipant();
    TrainingPlanContent practice = content(12L, "QUESTION_BANK", 1);
    bindContent(practice);
    link(12L, "QUESTION_BANK", 502L);
    TrainingAssessmentResult completed = new TrainingAssessmentResult();
    completed.setSourceRecordId(92L);
    completed.setSourceStatus("COMPLETED");
    completed.setFinalAttemptId(79L);
    completed.setEvaluatedCount(0);
    completed.setCorrectCount(0);
    completed.setWrongCount(0);
    completed.setUnassessedCount(2);
    completed.setAccuracyRate(null);
    completed.setCompletedTime(LocalDateTime.of(2026, 9, 11, 16, 0));
    when(assessmentPort.readResult(502L, 2L)).thenReturn(completed);
    TrainingParticipantContentResult existing = new TrainingParticipantContentResult();
    existing.setTrainingParticipantContentResultId(9L);
    existing.setTrainingPlanId(1L);
    existing.setTrainingParticipantId(2L);
    existing.setPlanContentId(12L);
    existing.setContentType("QUESTION_BANK");
    existing.setExecutionId(502L);
    existing.setSourceRecordType("EXAM_CANDIDATE");
    existing.setSourceRecordId(92L);
    existing.setFinalAttemptId(78L);
    existing.setEvaluatedCount(8);
    existing.setCorrectCount(6);
    existing.setWrongCount(2);
    existing.setUnassessedCount(2);
    existing.setAccuracyRate(new BigDecimal("75.00"));
    existing.setSourceStatus("COMPLETED");
    existing.setResultStatus("COMPLETED");
    existing.setEffectiveDurationSeconds(0);
    existing.setResultRevision(1);
    when(resultMapper.selectOne(any())).thenReturn(existing);
    when(resultMapper.selectList(any())).thenReturn(List.of(existing));

    service.synchronizeParticipantContent(2L, 12L);

    assertThat(existing.getFinalAttemptId()).isEqualTo(79L);
    assertThat(existing.getEvaluatedCount()).isZero();
    assertThat(existing.getUnassessedCount()).isEqualTo(2);
    assertThat(existing.getAccuracyRate()).isNull();
    assertThat(existing.getResultRevision()).isEqualTo(2);
  }

  @Test
  @DisplayName("未发布考试成绩不计分、不计入培训学时")
  void synchronize_unpublishedExam_excludesScoreAndHours() {
    planAndParticipant();
    TrainingPlanContent exam = content(11L, "EXAM", 1);
    stageWith(exam);
    link(11L, "EXAM", 501L);
    TrainingAssessmentResult unpublished = new TrainingAssessmentResult();
    unpublished.setSourceRecordId(91L);
    unpublished.setSourceStatus("SUBMITTED");
    unpublished.setFinalAttemptId(77L);
    when(assessmentPort.readResult(501L, 2L)).thenReturn(unpublished);
    when(resultMapper.selectOne(any())).thenReturn(null);
    when(resultMapper.selectList(any())).thenReturn(List.of());
    when(resultMapper.insert(any(TrainingParticipantContentResult.class))).thenAnswer(inv -> {
      TrainingParticipantContentResult row = inv.getArgument(0);
      row.setTrainingParticipantContentResultId(ids.getAndIncrement());
      when(resultMapper.selectList(any())).thenReturn(List.of(copy(row)));
      return 1;
    });

    List<TrainingParticipantResultsVO> results = service.synchronize(1L);

    ArgumentCaptor<TrainingParticipantContentResult> captor =
      ArgumentCaptor.forClass(TrainingParticipantContentResult.class);
    verify(resultMapper).insert(captor.capture());
    assertThat(captor.getValue().getScore()).isNull();
    assertThat(captor.getValue().getResultStatus()).isEqualTo("PENDING_PUBLISH");
    assertThat(captor.getValue().getSourceStatus()).isEqualTo("SUBMITTED");
    assertThat(captor.getValue().getEffectiveDurationSeconds()).isZero();
    assertThat(results.getFirst().getExamScore()).isNull();
    assertThat(results.getFirst().getOnlineHours()).isEqualByComparingTo("0.00");
  }

  @Test
  @DisplayName("课程学时计入 onlineHours，考试不重复计入")
  void synchronize_courseHours_onlyFromCourse() {
    planAndParticipant();
    TrainingPlanContent course = content(10L, "COURSE", 1);
    stageWith(course);
    link(10L, "COURSE", 401L);
    TrainingCourseResult enrollment = new TrainingCourseResult();
    enrollment.setSourceRecordId(81L);
    enrollment.setSourceStatus("COMPLETED");
    enrollment.setStudySeconds(3600);
    enrollment.setScore(new BigDecimal("80"));
    enrollment.setCompletedTime(LocalDateTime.of(2026, 8, 31, 10, 0, 0));
    when(coursePort.readResult(401L, 2L)).thenReturn(enrollment);
    when(resultMapper.selectOne(any())).thenReturn(null);
    when(resultMapper.selectList(any())).thenReturn(List.of());
    when(resultMapper.insert(any(TrainingParticipantContentResult.class))).thenAnswer(inv -> {
      TrainingParticipantContentResult row = inv.getArgument(0);
      row.setTrainingParticipantContentResultId(ids.getAndIncrement());
      when(resultMapper.selectList(any())).thenReturn(List.of(copy(row)));
      return 1;
    });

    TrainingParticipantResultsVO result = service.synchronize(1L).getFirst();

    assertThat(result.getOnlineHours()).isEqualByComparingTo("1.00");
    assertThat(result.getCourseScore()).isEqualByComparingTo("80");
    assertThat(result.getItems().getFirst().getSourceRecordType()).isEqualTo("COURSE_ENROLLMENT");
    assertThat(result.getItems().getFirst().getSessionStatus()).isNull();
  }

  @Test
  @DisplayName("线下无成绩时待开始且获得学时为 0")
  void synchronize_offlineWithoutScore_pendingZeroHours() {
    TrainingParticipantResultsVO result = synchronizeOfflineScore(null, null);

    assertThat(result.getOfflineHours()).isEqualByComparingTo("0.00");
    assertThat(result.getItems().getFirst().getEffectiveDurationSeconds()).isZero();
    assertThat(result.getItems().getFirst().getResultStatus()).isEqualTo("PENDING");
    assertThat(result.getItems().getFirst().getSessionStatus()).isEqualTo("IN_PROGRESS");
  }

  @Test
  @DisplayName("线下内容未关联场次时不伪造场次状态")
  void synchronize_offlineWithoutSession_sessionStatusNull() {
    TrainingParticipantResultsVO result = synchronizeOfflineScore(null, null, null);

    assertThat(result.getItems().getFirst().getResultStatus()).isEqualTo("PENDING");
    assertThat(result.getItems().getFirst().getSessionStatus()).isNull();
  }

  @Test
  @DisplayName("线下已评分无及格线按安排学时完成")
  void synchronize_offlineScored_countsRequiredHours() {
    TrainingParticipantResultsVO result = synchronizeOfflineScore(new BigDecimal("90"), null);

    assertThat(result.getItems().getFirst().getEffectiveDurationSeconds()).isEqualTo(7200);
    assertThat(result.getOfflineHours()).isEqualByComparingTo("2.00");
    assertThat(result.getItems().getFirst().getResultStatus()).isEqualTo("COMPLETED");
    assertThat(result.getItems().getFirst().getSourceRecordType()).isEqualTo("OFFLINE_SCORE");
  }

  @Test
  @DisplayName("线下低于及格线 FAILED 且不计获得学时")
  void synchronize_offlineBelowPassScore_failedZeroHours() {
    TrainingParticipantResultsVO result = synchronizeOfflineScore(new BigDecimal("50"), new BigDecimal("60"));

    assertThat(result.getOfflineHours()).isEqualByComparingTo("0.00");
    assertThat(result.getItems().getFirst().getEffectiveDurationSeconds()).isZero();
    assertThat(result.getItems().getFirst().getResultStatus()).isEqualTo("FAILED");
  }

  @Test
  @DisplayName("已冻结参训人普通同步不得覆盖投影")
  void synchronize_finalized_skipsUpdate() {
    planAndParticipant();
    TrainingParticipant frozen = new TrainingParticipant();
    frozen.setTrainingParticipantId(2L);
    frozen.setTrainingPlanId(1L);
    frozen.setResultFinalizedTime(LocalDateTime.now());
    frozen.setOnlineHours(new BigDecimal("1.00"));
    when(participantMapper.selectList(any())).thenReturn(List.of(frozen));
    when(stageMapper.selectList(any())).thenReturn(List.of());
    when(resultMapper.selectList(any())).thenReturn(List.of());

    service.synchronize(1L);

    verify(resultMapper, never()).insert(any(TrainingParticipantContentResult.class));
    verify(resultMapper, never()).updateById(any(TrainingParticipantContentResult.class));
    verify(participantMapper, never()).updateById(any(TrainingParticipant.class));
  }

  @Test
  @DisplayName("结束冻结：考试不计培训学时，全部必修完成仍 PASSED")
  void freezePlan_examWithoutTrainingHours_stillPassed() {
    planAndParticipant();
    TrainingPlanContent course = content(10L, "COURSE", 1);
    TrainingPlanContent exam = content(11L, "EXAM", 1);
    stageWith(course, exam);
    TrainingParticipantContentResult courseRow = new TrainingParticipantContentResult();
    courseRow.setPlanContentId(10L);
    courseRow.setContentType("COURSE");
    courseRow.setExecutionId(401L);
    courseRow.setSourceRecordType("COURSE_ENROLLMENT");
    courseRow.setSourceRecordId(81L);
    courseRow.setSourceStatus("COMPLETED");
    courseRow.setResultStatus("COMPLETED");
    courseRow.setEffectiveDurationSeconds(3600);
    courseRow.setScore(new BigDecimal("80.00"));
    TrainingParticipantContentResult examRow = new TrainingParticipantContentResult();
    examRow.setPlanContentId(11L);
    examRow.setContentType("EXAM");
    examRow.setExecutionId(501L);
    examRow.setSourceRecordType("EXAM_CANDIDATE");
    examRow.setSourceRecordId(91L);
    examRow.setSourceStatus("PASSED");
    examRow.setResultStatus("COMPLETED");
    examRow.setEffectiveDurationSeconds(0);
    examRow.setScore(new BigDecimal("90.00"));
    when(resultMapper.selectList(any())).thenReturn(List.of(courseRow, examRow));

    service.freezePlan(1L);

    ArgumentCaptor<TrainingParticipant> captor = ArgumentCaptor.forClass(TrainingParticipant.class);
    verify(participantMapper).updateById(captor.capture());
    assertThat(captor.getValue().getResultStatus()).isEqualTo("PASSED");
    assertThat(captor.getValue().getOnlineHours()).isEqualByComparingTo("1.00");
    assertThat(captor.getValue().getExamScore()).isEqualByComparingTo("90.00");
    assertThat(captor.getValue().getFinalResultSnapshotJson()).contains("\"schemaVersion\":3");
    assertThat(captor.getValue().getFinalResultSnapshotJson()).doesNotContain("INCOMPLETE");
  }

  @Test
  @DisplayName("结束冻结生成带 schemaVersion 的规范化 hash")
  void freezePlan_writesCanonicalSnapshotHash() {
    planAndParticipant();
    TrainingPlanContent course = content(10L, "COURSE", 1);
    stageWith(course);
    TrainingParticipantContentResult row = new TrainingParticipantContentResult();
    row.setPlanContentId(10L);
    row.setContentType("COURSE");
    row.setExecutionId(401L);
    row.setSourceRecordType("COURSE_ENROLLMENT");
    row.setSourceRecordId(81L);
    row.setResultStatus("COMPLETED");
    row.setEffectiveDurationSeconds(3600);
    row.setScore(new BigDecimal("80.00"));
    row.setEvaluatedCount(8);
    row.setCorrectCount(6);
    row.setWrongCount(2);
    row.setUnassessedCount(2);
    row.setAccuracyRate(new BigDecimal("75.00"));
    row.setCompletedTime(LocalDateTime.of(2026, 8, 31, 10, 0, 0));
    when(resultMapper.selectList(any())).thenReturn(List.of(row));

    service.freezePlan(1L);

    ArgumentCaptor<TrainingParticipant> captor = ArgumentCaptor.forClass(TrainingParticipant.class);
    verify(participantMapper).updateById(captor.capture());
    assertThat(captor.getValue().getFinalResultSnapshotJson()).contains("\"schemaVersion\":3");
    assertThat(captor.getValue().getFinalResultSnapshotJson()).contains("\"sourceStatus\"");
    assertThat(captor.getValue().getFinalResultSnapshotJson())
      .contains("\"evaluatedCount\":8", "\"unassessedCount\":2", "\"accuracyRate\":\"75.00\"");
    assertThat(captor.getValue().getFinalResultSnapshotHash()).hasSize(64);
    assertThat(captor.getValue().getResultStatus()).isEqualTo("PASSED");
    assertThat(captor.getValue().getResultFinalizedTime()).isNotNull();
    verify(resultMapper).updateById(any(TrainingParticipantContentResult.class));
    when(participantMapper.selectById(2L)).thenReturn(captor.getValue());
    var archivedItem = service.getResults(1L, 2L).getItems().getFirst();
    assertThat(archivedItem.getEvaluatedCount()).isEqualTo(8);
    assertThat(archivedItem.getUnassessedCount()).isEqualTo(2);
    assertThat(archivedItem.getAccuracyRate()).isEqualByComparingTo("75.00");
  }

  @Test
  @DisplayName("已发布 Candidate FAILED 映射为 FAILED，不以本地及格线改写")
  void synchronize_publishedFailedExam_mapsFailed() {
    planAndParticipant();
    TrainingPlanContent exam = content(11L, "EXAM", 1);
    exam.setExecutionConfigJson("{\"passScore\":50}");
    stageWith(exam);
    link(11L, "EXAM", 501L);
    TrainingAssessmentResult published = new TrainingAssessmentResult();
    published.setSourceRecordId(91L);
    published.setSourceStatus("FAILED");
    published.setScore(new BigDecimal("59"));
    published.setFinalAttemptId(77L);
    published.setCompletedTime(LocalDateTime.of(2026, 8, 31, 11, 0, 0));
    when(assessmentPort.readResult(501L, 2L)).thenReturn(published);
    when(resultMapper.selectOne(any())).thenReturn(null);
    when(resultMapper.selectList(any())).thenReturn(List.of());
    when(resultMapper.insert(any(TrainingParticipantContentResult.class))).thenAnswer(inv -> {
      TrainingParticipantContentResult row = inv.getArgument(0);
      when(resultMapper.selectList(any())).thenReturn(List.of(copy(row)));
      return 1;
    });

    service.synchronize(1L);

    ArgumentCaptor<TrainingParticipantContentResult> captor =
      ArgumentCaptor.forClass(TrainingParticipantContentResult.class);
    verify(resultMapper).insert(captor.capture());
    assertThat(captor.getValue().getResultStatus()).isEqualTo("FAILED");
    assertThat(captor.getValue().getSourceStatus()).isEqualTo("FAILED");
    assertThat(captor.getValue().getScore()).isEqualByComparingTo("59");
  }

  @Test
  @DisplayName("已发布 Candidate PASSED 投影为 sourceStatus=PASSED 且 resultStatus=COMPLETED")
  void synchronize_publishedPassedExam_keepsDistinctStatuses() {
    planAndParticipant();
    TrainingPlanContent exam = content(11L, "EXAM", 1);
    stageWith(exam);
    link(11L, "EXAM", 501L);
    when(assessmentPort.readResult(501L, 2L)).thenReturn(publishedPassed(new BigDecimal("80")));
    when(resultMapper.selectOne(any())).thenReturn(null);
    when(resultMapper.selectList(any())).thenReturn(List.of());
    when(resultMapper.insert(any(TrainingParticipantContentResult.class))).thenAnswer(inv -> {
      TrainingParticipantContentResult row = inv.getArgument(0);
      when(resultMapper.selectList(any())).thenReturn(List.of(copy(row)));
      return 1;
    });

    TrainingParticipantResultsVO result = service.synchronize(1L).getFirst();

    ArgumentCaptor<TrainingParticipantContentResult> captor =
      ArgumentCaptor.forClass(TrainingParticipantContentResult.class);
    verify(resultMapper).insert(captor.capture());
    assertThat(captor.getValue().getSourceStatus()).isEqualTo("PASSED");
    assertThat(captor.getValue().getResultStatus()).isEqualTo("COMPLETED");
    assertThat(result.getItems().getFirst().getSourceStatus()).isEqualTo("PASSED");
    assertThat(result.getItems().getFirst().getResultStatus()).isEqualTo("COMPLETED");
  }

  @Test
  @DisplayName("不评分题库练习 COMPLETED 直接投影完成且不写分数")
  void synchronize_completedQuestionBankWithoutScore() {
    planAndParticipant();
    TrainingPlanContent practice = content(12L, "QUESTION_BANK", 1);
    stageWith(practice);
    link(12L, "QUESTION_BANK", 502L);
    TrainingAssessmentResult completed = new TrainingAssessmentResult();
    completed.setSourceRecordId(92L);
    completed.setSourceStatus("COMPLETED");
    completed.setFinalAttemptId(78L);
    completed.setEvaluatedCount(8);
    completed.setCorrectCount(6);
    completed.setWrongCount(2);
    completed.setUnassessedCount(2);
    completed.setAccuracyRate(new BigDecimal("75.00"));
    completed.setCompletedTime(LocalDateTime.of(2026, 9, 11, 15, 0));
    when(assessmentPort.readResult(502L, 2L)).thenReturn(completed);
    when(resultMapper.selectOne(any())).thenReturn(null);
    when(resultMapper.selectList(any())).thenReturn(List.of());
    when(resultMapper.insert(any(TrainingParticipantContentResult.class))).thenAnswer(invocation -> {
      TrainingParticipantContentResult row = invocation.getArgument(0);
      when(resultMapper.selectList(any())).thenReturn(List.of(copy(row)));
      return 1;
    });

    service.synchronize(1L);

    ArgumentCaptor<TrainingParticipantContentResult> captor =
      ArgumentCaptor.forClass(TrainingParticipantContentResult.class);
    verify(resultMapper).insert(captor.capture());
    assertThat(captor.getValue().getSourceStatus()).isEqualTo("COMPLETED");
    assertThat(captor.getValue().getResultStatus()).isEqualTo("COMPLETED");
    assertThat(captor.getValue().getScore()).isNull();
    assertThat(captor.getValue().getFinalAttemptId()).isEqualTo(78L);
    assertThat(captor.getValue().getEvaluatedCount()).isEqualTo(8);
    assertThat(captor.getValue().getCorrectCount()).isEqualTo(6);
    assertThat(captor.getValue().getWrongCount()).isEqualTo(2);
    assertThat(captor.getValue().getUnassessedCount()).isEqualTo(2);
    assertThat(captor.getValue().getAccuracyRate()).isEqualByComparingTo("75.00");
    verify(participantMapper).updateById(argThat((TrainingParticipant participant) ->
      "LEARNING".equals(participant.getParticipantStatus())));
  }

  @Test
  @DisplayName("线下已评分即可完成，不看出勤")
  void synchronize_offlineScore_completed() {
    TrainingParticipantResultsVO result = synchronizeOfflineScore(new BigDecimal("90"), new BigDecimal("60"));

    assertThat(result.getItems().getFirst().getResultStatus()).isEqualTo("COMPLETED");
    assertThat(result.getItems().getFirst().getEffectiveDurationSeconds()).isEqualTo(7200);
  }

  @Test
  @DisplayName("WEIGHTED_AVERAGE 按权重计算，80×1 与 100×3 为 95")
  void synchronize_weightedAverage_usesWeights() {
    planAndParticipant();
    TrainingPlanContent first = content(21L, "EXAM", 1);
    first.setScoreAggregationMode("WEIGHTED_AVERAGE");
    first.setScoreWeight(BigDecimal.ONE);
    first.setSortOrder(1);
    TrainingPlanContent second = content(22L, "EXAM", 1);
    second.setScoreAggregationMode("WEIGHTED_AVERAGE");
    second.setScoreWeight(new BigDecimal("3"));
    second.setSortOrder(2);
    TrainingPlanStage stage = new TrainingPlanStage();
    stage.setPlanStageId(5L);
    stage.setTrainingPlanId(1L);
    when(stageMapper.selectList(any())).thenReturn(List.of(stage));
    when(contentMapper.selectList(any())).thenReturn(List.of(first, second));
    TrainingExecutionLink link1 = new TrainingExecutionLink();
    link1.setPlanContentId(21L);
    link1.setExecutionType("EXAM");
    link1.setExecutionId(501L);
    TrainingExecutionLink link2 = new TrainingExecutionLink();
    link2.setPlanContentId(22L);
    link2.setExecutionType("EXAM");
    link2.setExecutionId(502L);
    when(executionLinkMapper.selectOne(any())).thenReturn(link1, link2);
    TrainingAssessmentResult r1 = publishedPassed(new BigDecimal("80"));
    TrainingAssessmentResult r2 = publishedPassed(new BigDecimal("100"));
    when(assessmentPort.readResult(501L, 2L)).thenReturn(r1);
    when(assessmentPort.readResult(502L, 2L)).thenReturn(r2);
    when(resultMapper.selectOne(any())).thenReturn(null);
    List<TrainingParticipantContentResult> stored = new ArrayList<>();
    when(resultMapper.selectList(any())).thenAnswer(inv -> stored.stream().map(this::copy).toList());
    when(resultMapper.insert(any(TrainingParticipantContentResult.class))).thenAnswer(inv -> {
      TrainingParticipantContentResult row = inv.getArgument(0);
      stored.add(copy(row));
      return 1;
    });

    TrainingParticipantResultsVO result = service.synchronize(1L).getFirst();

    assertThat(result.getExamScore()).isEqualByComparingTo("95.00");
  }

  @Test
  @DisplayName("结束快照含 finalizedTime 且与投影、参训人归档时间一致")
  void freezePlan_snapshotIncludesMatchingFinalizedTime() throws Exception {
    planAndParticipant();
    TrainingPlanContent course = content(10L, "COURSE", 1);
    stageWith(course);
    TrainingParticipantContentResult row = new TrainingParticipantContentResult();
    row.setPlanContentId(10L);
    row.setContentType("COURSE");
    row.setExecutionId(401L);
    row.setResultStatus("COMPLETED");
    row.setEffectiveDurationSeconds(0);
    when(resultMapper.selectList(any())).thenReturn(List.of(row));

    service.freezePlan(1L);

    ArgumentCaptor<TrainingParticipant> participantCaptor = ArgumentCaptor.forClass(TrainingParticipant.class);
    verify(participantMapper).updateById(participantCaptor.capture());
    ArgumentCaptor<TrainingParticipantContentResult> rowCaptor =
      ArgumentCaptor.forClass(TrainingParticipantContentResult.class);
    verify(resultMapper).updateById(rowCaptor.capture());
    String json = participantCaptor.getValue().getFinalResultSnapshotJson();
    assertThat(json).contains("\"finalizedTime\"");
    String expected = TrainingResultSyncServiceImpl.SNAPSHOT_TIME.format(
      participantCaptor.getValue().getResultFinalizedTime());
    assertThat(json).contains("\"finalizedTime\":\"" + expected + "\"");
    assertThat(rowCaptor.getValue().getFinalizedTime())
      .isEqualTo(participantCaptor.getValue().getResultFinalizedTime());
    String originalHash = participantCaptor.getValue().getFinalResultSnapshotHash();
    participantCaptor.getValue().setResultFinalizedTime(participantCaptor.getValue().getResultFinalizedTime());
    when(participantMapper.selectList(any())).thenReturn(List.of(participantCaptor.getValue()));
    when(participantMapper.selectById(2L)).thenReturn(participantCaptor.getValue());
    service.synchronize(1L);
    verify(participantMapper, times(1)).updateById(any(TrainingParticipant.class));
    assertThat(participantCaptor.getValue().getFinalResultSnapshotHash()).isEqualTo(originalHash);
  }

  @Test
  @DisplayName("归档后查询优先读取 v1 最终快照，不猜测 sourceStatus、不改写快照")
  void getResults_archivedV1Snapshot_readableWithoutGuessingSourceStatus() {
    planAndParticipant();
    String json = v1SnapshotJson();
    TrainingParticipant frozen = archivedParticipant(json);
    when(participantMapper.selectById(2L)).thenReturn(frozen);
    TrainingPlanContent course = content(10L, "COURSE", 1);
    stageWith(course);
    when(resultMapper.selectList(any())).thenReturn(List.of(projectionGuessingSourceStatus()));

    TrainingParticipantResultsVO result = service.getResults(1L, 2L);

    assertThat(result.getResultStatus()).isEqualTo("PASSED");
    assertThat(result.getCourseScore()).isEqualByComparingTo("80.00");
    assertThat(result.getItems()).hasSize(1);
    assertThat(result.getItems().getFirst().getSourceStatus()).isNull();
    assertThat(result.getItems().getFirst().getResultStatus()).isEqualTo("COMPLETED");
    assertThat(result.getItems().getFirst().getScore()).isEqualByComparingTo("80.00");
    assertThat(frozen.getFinalResultSnapshotJson()).isEqualTo(json);
    assertThat(frozen.getFinalResultSnapshotHash()).isEqualTo(TrainingResultSyncServiceImpl.sha256Hex(json));
    verify(resultMapper, never()).updateById(any(TrainingParticipantContentResult.class));
    verify(participantMapper, never()).updateById(any(TrainingParticipant.class));
  }

  @Test
  @DisplayName("归档后查询读取合法 v2 最终快照并保留 sourceStatus")
  void getResults_archivedV2Snapshot_keepsSourceStatus() {
    planAndParticipant();
    String json = v2SnapshotJson();
    TrainingParticipant frozen = archivedParticipant(json);
    when(participantMapper.selectById(2L)).thenReturn(frozen);
    TrainingPlanContent exam = content(11L, "EXAM", 1);
    stageWith(exam);

    TrainingParticipantResultsVO result = service.getResults(1L, 2L);

    assertThat(result.getItems().getFirst().getSourceStatus()).isEqualTo("PASSED");
    assertThat(result.getItems().getFirst().getResultStatus()).isEqualTo("COMPLETED");
    assertThat(result.getItems().getFirst().getScore()).isEqualByComparingTo("80.00");
    assertThat(frozen.getFinalResultSnapshotJson()).isEqualTo(json);
    verify(resultMapper, never()).selectList(any());
    verify(participantMapper, never()).updateById(any(TrainingParticipant.class));
  }

  @Test
  @DisplayName("归档 v1 快照被篡改时 8111 拒绝，不回退投影、不改写")
  void getResults_tamperedV1Snapshot_rejectsWithoutFallback() {
    planAndParticipant();
    String original = v1SnapshotJson();
    TrainingParticipant frozen = archivedParticipant(original);
    frozen.setFinalResultSnapshotJson(original.replace("\"score\":\"80.00\"", "\"score\":\"1.00\""));
    when(participantMapper.selectById(2L)).thenReturn(frozen);
    TrainingPlanContent course = content(10L, "COURSE", 1);
    stageWith(course);
    when(resultMapper.selectList(any())).thenReturn(List.of(projectionGuessingSourceStatus()));

    assertIntegrityRejected(() -> service.getResults(1L, 2L));
    assertThat(frozen.getFinalResultSnapshotHash()).isEqualTo(TrainingResultSyncServiceImpl.sha256Hex(original));
    verify(resultMapper, never()).selectList(any());
    verifyNoUpdates();
  }

  @Test
  @DisplayName("归档 v2 快照被篡改时 8111 拒绝，不回退投影、不改写")
  void getResults_tamperedV2Snapshot_rejectsWithoutFallback() {
    planAndParticipant();
    String original = v2SnapshotJson();
    TrainingParticipant frozen = archivedParticipant(original);
    frozen.setFinalResultSnapshotJson(original.replace("\"sourceStatus\":\"PASSED\"", "\"sourceStatus\":\"FAILED\""));
    when(participantMapper.selectById(2L)).thenReturn(frozen);
    TrainingPlanContent exam = content(11L, "EXAM", 1);
    stageWith(exam);

    assertIntegrityRejected(() -> service.getResults(1L, 2L));
    verify(resultMapper, never()).selectList(any());
    verifyNoUpdates();
  }

  @Test
  @DisplayName("归档快照 hash 缺失或非法时 8111 拒绝")
  void getResults_missingFinalSnapshotHash_rejects() {
    planAndParticipant();
    TrainingParticipant frozen = archivedParticipant(v1SnapshotJson());
    frozen.setFinalResultSnapshotHash("not-a-sha256");
    when(participantMapper.selectById(2L)).thenReturn(frozen);
    TrainingPlanContent course = content(10L, "COURSE", 1);
    stageWith(course);

    assertIntegrityRejected(() -> service.getResults(1L, 2L));
    verify(resultMapper, never()).selectList(any());
    verifyNoUpdates();
  }

  @Test
  @DisplayName("未归档参训人继续读取实时投影，不受最终快照 hash 影响")
  void getResults_unarchived_readsProjectionWithoutFinalSnapshotHash() {
    planAndParticipant();
    TrainingPlanContent course = content(10L, "COURSE", 1);
    stageWith(course);
    when(resultMapper.selectList(any())).thenReturn(List.of(projectionGuessingSourceStatus()));

    TrainingParticipantResultsVO result = service.getResults(1L, 2L);

    assertThat(result.getItems().getFirst().getScore()).isEqualByComparingTo("99.00");
    assertThat(result.getItems().getFirst().getSourceStatus()).isEqualTo("COMPLETED");
    verify(participantMapper, never()).updateById(any(TrainingParticipant.class));
  }

  @Test
  @DisplayName("实时投影已有考试和线下正式成绩时返回实时培训结果，不显示待同步")
  void getResults_unfinalizedCompletedExamAndOffline_derivesParticipantResultStatus() {
    planAndParticipant();
    TrainingPlanContent exam = content(11L, "EXAM", 1);
    TrainingPlanContent offline = content(12L, "OFFLINE", 1);
    stageWith(exam, offline);
    TrainingParticipantContentResult examRow = projectionGuessingSourceStatus();
    examRow.setPlanContentId(11L);
    examRow.setContentType("EXAM");
    examRow.setResultStatus("COMPLETED");
    examRow.setScore(new BigDecimal("48"));
    TrainingParticipantContentResult offlineRow = projectionGuessingSourceStatus();
    offlineRow.setPlanContentId(12L);
    offlineRow.setContentType("OFFLINE");
    offlineRow.setResultStatus("COMPLETED");
    offlineRow.setScore(new BigDecimal("60"));
    when(resultMapper.selectList(any())).thenReturn(List.of(examRow, offlineRow));

    TrainingParticipantResultsVO result = service.getResults(1L, 2L);

    assertThat(result.getExamScore()).isEqualByComparingTo("48");
    assertThat(result.getOfflineScore()).isEqualByComparingTo("60");
    assertThat(result.getResultStatus()).isEqualTo("PASSED");
  }

  @Test
  @DisplayName("已归档 v1 最终快照在再次冻结时不改写")
  void freezePlan_existingV1Snapshot_notRewritten() {
    planAndParticipant();
    String json = v1SnapshotJson();
    TrainingParticipant frozen = archivedParticipant(json);
    when(participantMapper.selectList(any())).thenReturn(List.of(frozen));
    TrainingPlanContent course = content(10L, "COURSE", 1);
    stageWith(course);
    when(resultMapper.selectList(any())).thenReturn(List.of());

    service.freezePlan(1L);

    verify(participantMapper, never()).updateById(any(TrainingParticipant.class));
    assertThat(frozen.getFinalResultSnapshotJson()).isEqualTo(json);
    assertThat(frozen.getFinalResultSnapshotHash()).isEqualTo(TrainingResultSyncServiceImpl.sha256Hex(json));
  }

  private TrainingAssessmentResult publishedPassed(BigDecimal score) {
    TrainingAssessmentResult result = new TrainingAssessmentResult();
    result.setSourceRecordId(91L);
    result.setSourceStatus("PASSED");
    result.setScore(score);
    result.setCompletedTime(LocalDateTime.of(2026, 8, 31, 11, 0, 0));
    return result;
  }

  private void planAndParticipant() {
    TrainingPlan plan = new TrainingPlan();
    plan.setTrainingPlanId(1L);
    plan.setPublishStatus("RUNNING");
    when(planMapper.selectById(1L)).thenReturn(plan);
    TrainingParticipant participant = new TrainingParticipant();
    participant.setTrainingParticipantId(2L);
    participant.setTrainingPlanId(1L);
    participant.setParticipantStatus("PENDING");
    participant.setOnlineHours(BigDecimal.ZERO);
    participant.setOfflineHours(BigDecimal.ZERO);
    when(participantMapper.selectList(any())).thenReturn(List.of(participant));
    when(participantMapper.selectById(2L)).thenReturn(participant);
  }

  private TrainingParticipant archivedParticipant(String json) {
    TrainingParticipant frozen = new TrainingParticipant();
    frozen.setTrainingParticipantId(2L);
    frozen.setTrainingPlanId(1L);
    frozen.setResultStatus("PASSED");
    frozen.setResultFinalizedTime(LocalDateTime.of(2026, 8, 31, 12, 0, 0));
    frozen.setOnlineHours(new BigDecimal("1.00"));
    frozen.setCourseScore(new BigDecimal("80.00"));
    frozen.setFinalResultSnapshotJson(json);
    frozen.setFinalResultSnapshotHash(TrainingResultSyncServiceImpl.sha256Hex(json));
    return frozen;
  }

  private String v1SnapshotJson() {
    return "{\"schemaVersion\":1,\"trainingPlanId\":1,\"trainingParticipantId\":2,\"resultStatus\":\"PASSED\","
      + "\"finalizedTime\":\"2026-08-31T12:00:00\",\"onlineHours\":\"1.00\",\"offlineHours\":\"0.00\","
      + "\"courseScore\":\"80.00\",\"examScore\":null,\"offlineScore\":null,\"compositeScore\":null,"
      + "\"officialCalculationRunId\":null,\"items\":[{\"planContentId\":10,\"contentType\":\"COURSE\","
      + "\"executionId\":401,\"sourceRecordType\":\"COURSE_ENROLLMENT\",\"sourceRecordId\":81,"
      + "\"finalAttemptId\":null,\"resultStatus\":\"COMPLETED\",\"effectiveDurationSeconds\":3600,"
      + "\"score\":\"80.00\",\"completedTime\":\"2026-08-31T10:00:00\"}]}";
  }

  private String v2SnapshotJson() {
    return "{\"schemaVersion\":2,\"trainingPlanId\":1,\"trainingParticipantId\":2,\"resultStatus\":\"PASSED\","
      + "\"finalizedTime\":\"2026-08-31T12:00:00\",\"onlineHours\":\"0.00\",\"offlineHours\":\"0.00\","
      + "\"courseScore\":null,\"examScore\":\"80.00\",\"offlineScore\":null,\"compositeScore\":null,"
      + "\"officialCalculationRunId\":null,\"items\":[{\"planContentId\":11,\"contentType\":\"EXAM\","
      + "\"executionId\":501,\"sourceRecordType\":\"EXAM_CANDIDATE\",\"sourceRecordId\":91,"
      + "\"finalAttemptId\":201,\"sourceStatus\":\"PASSED\",\"resultStatus\":\"COMPLETED\","
      + "\"effectiveDurationSeconds\":0,\"score\":\"80.00\",\"completedTime\":\"2026-08-31T11:00:00\"}]}";
  }

  private TrainingParticipantContentResult projectionGuessingSourceStatus() {
    TrainingParticipantContentResult row = new TrainingParticipantContentResult();
    row.setPlanContentId(10L);
    row.setContentType("COURSE");
    row.setSourceStatus("COMPLETED");
    row.setResultStatus("COMPLETED");
    row.setScore(new BigDecimal("99.00"));
    row.setEffectiveDurationSeconds(0);
    return row;
  }

  private void assertIntegrityRejected(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
    assertThatThrownBy(action)
      .isInstanceOfSatisfying(BusinessException.class, ex -> {
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(ex.getCode()).isEqualTo(8111);
        assertThat(ex.getMessage()).contains("最终结果快照完整性校验失败");
      });
  }

  private void verifyNoUpdates() {
    verify(resultMapper, never()).updateById(any(TrainingParticipantContentResult.class));
    verify(participantMapper, never()).updateById(any(TrainingParticipant.class));
  }

  private TrainingPlanContent content(Long id, String type, int required) {
    TrainingPlanContent content = new TrainingPlanContent();
    content.setPlanContentId(id);
    content.setPlanStageId(5L);
    content.setContentType(type);
    content.setContentName(type);
    content.setIsRequired(required);
    content.setIsScored(1);
    content.setSortOrder(1);
    return content;
  }

  private void bindContent(TrainingPlanContent content) {
    when(contentMapper.selectById(content.getPlanContentId())).thenReturn(content);
    TrainingPlanStage stage = new TrainingPlanStage();
    stage.setPlanStageId(content.getPlanStageId());
    stage.setTrainingPlanId(1L);
    when(stageMapper.selectById(content.getPlanStageId())).thenReturn(stage);
    stageWith(content);
  }

  private void stageWith(TrainingPlanContent... contents) {
    TrainingPlanStage stage = new TrainingPlanStage();
    stage.setPlanStageId(5L);
    stage.setTrainingPlanId(1L);
    when(stageMapper.selectList(any())).thenReturn(List.of(stage));
    when(contentMapper.selectList(any())).thenReturn(List.of(contents));
  }

  private void link(Long contentId, String type, Long executionId) {
    TrainingExecutionLink link = new TrainingExecutionLink();
    link.setPlanContentId(contentId);
    link.setExecutionType(type);
    link.setExecutionId(executionId);
    when(executionLinkMapper.selectOne(any())).thenReturn(link);
  }

  private TrainingParticipantResultsVO synchronizeOfflineScore(BigDecimal scoreValue, BigDecimal passScore) {
    return synchronizeOfflineScore(scoreValue, passScore, "IN_PROGRESS");
  }

  /** 构造线下成绩投影测试数据；场次状态为空表示内容尚未关联线下场次。 */
  private TrainingParticipantResultsVO synchronizeOfflineScore(BigDecimal scoreValue, BigDecimal passScore,
                                                                String sessionStatus) {
    planAndParticipant();
    TrainingPlanContent offline = content(12L, "OFFLINE", 1);
    offline.setRequiredHours(new BigDecimal("2"));
    if (passScore != null) {
      offline.setExecutionConfigJson("{\"passScore\":" + passScore.toPlainString() + "}");
    }
    stageWith(offline);
    if (sessionStatus == null) {
      when(offlineSessionMapper.selectOne(any())).thenReturn(null);
      when(offlineSessionMapper.selectList(any())).thenReturn(List.of());
    } else {
      TrainingOfflineSession session = new TrainingOfflineSession();
      session.setOfflineSessionId(601L);
      session.setPlanContentId(12L);
      session.setSessionStatus(sessionStatus);
      when(offlineSessionMapper.selectOne(any())).thenReturn(session);
      when(offlineSessionMapper.selectList(any())).thenReturn(List.of(session));
    }
    if (scoreValue == null) {
      when(offlineScoreMapper.selectOne(any())).thenReturn(null);
    } else {
      TrainingOfflineScore score = new TrainingOfflineScore();
      score.setOfflineScoreId(81L);
      score.setScore(scoreValue);
      score.setScoredBy(9L);
      score.setScoredTime(LocalDateTime.now());
      when(offlineScoreMapper.selectOne(any())).thenReturn(score);
    }
    stubEmptyProjectionInsert();
    return service.synchronize(1L).getFirst();
  }

  private void stubEmptyProjectionInsert() {
    when(resultMapper.selectOne(any())).thenReturn(null);
    when(resultMapper.selectList(any())).thenReturn(List.of());
    when(resultMapper.insert(any(TrainingParticipantContentResult.class))).thenAnswer(inv -> {
      TrainingParticipantContentResult row = inv.getArgument(0);
      when(resultMapper.selectList(any())).thenReturn(List.of(copy(row)));
      return 1;
    });
  }

  private TrainingParticipantContentResult copy(TrainingParticipantContentResult row) {
    TrainingParticipantContentResult copy = new TrainingParticipantContentResult();
    copy.setTrainingParticipantContentResultId(row.getTrainingParticipantContentResultId());
    copy.setTrainingPlanId(row.getTrainingPlanId());
    copy.setTrainingParticipantId(row.getTrainingParticipantId());
    copy.setPlanContentId(row.getPlanContentId());
    copy.setContentType(row.getContentType());
    copy.setExecutionId(row.getExecutionId());
    copy.setSourceRecordType(row.getSourceRecordType());
    copy.setSourceRecordId(row.getSourceRecordId());
    copy.setFinalAttemptId(row.getFinalAttemptId());
    copy.setEvaluatedCount(row.getEvaluatedCount());
    copy.setCorrectCount(row.getCorrectCount());
    copy.setWrongCount(row.getWrongCount());
    copy.setUnassessedCount(row.getUnassessedCount());
    copy.setAccuracyRate(row.getAccuracyRate());
    copy.setSourceStatus(row.getSourceStatus());
    copy.setResultStatus(row.getResultStatus());
    copy.setEffectiveDurationSeconds(row.getEffectiveDurationSeconds());
    copy.setScore(row.getScore());
    copy.setCompletedTime(row.getCompletedTime());
    return copy;
  }
}

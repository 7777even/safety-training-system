package com.sts.exam.service;

import com.sts.exam.entity.AssessmentExecution;
import com.sts.exam.entity.Exam;
import com.sts.exam.entity.ExamCandidate;
import com.sts.exam.entity.ExamPolicy;
import com.sts.exam.mapper.*;
import com.sts.training.port.TrainingExamExecutionCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TrainingAssessmentExecutionAdapterTest {

  private ExamMapper examMapper;
  private ExamPolicyMapper examPolicyMapper;
  private ExamReviewerMapper examReviewerMapper;
  private ExamCandidateMapper examCandidateMapper;
  private ExamAttemptMapper examAttemptMapper;
  private AssessmentExecutionService assessmentExecutionService;
  private ExamService examService;
  private AssessmentExecutionQuestionPoolService questionPoolService;
  private TrainingAssessmentExecutionAdapter adapter;

  @BeforeEach
  void setUp() {
    examMapper = mock(ExamMapper.class);
    examPolicyMapper = mock(ExamPolicyMapper.class);
    examReviewerMapper = mock(ExamReviewerMapper.class);
    examCandidateMapper = mock(ExamCandidateMapper.class);
    examAttemptMapper = mock(ExamAttemptMapper.class);
    assessmentExecutionService = mock(AssessmentExecutionService.class);
    examService = mock(ExamService.class);
    questionPoolService = mock(AssessmentExecutionQuestionPoolService.class);
    adapter = new TrainingAssessmentExecutionAdapter(
      examMapper, examPolicyMapper, examCandidateMapper, examAttemptMapper,
      examReviewerMapper,
      assessmentExecutionService, examService, mock(ExamAttemptService.class),
      mock(ExamCandidateProfileSnapshotAssembler.class),
      questionPoolService);
  }

  private void stubNewExamPublish(Long examId, AssessmentExecution execution) {
    when(examMapper.selectOne(any())).thenReturn(null);
    when(examMapper.insert(any(Exam.class))).thenAnswer(inv -> {
      ((Exam) inv.getArgument(0)).setExamId(examId);
      return 1;
    });
    when(assessmentExecutionService.requireStandaloneByExamId(examId)).thenReturn(execution);
  }

  @Test
  @DisplayName("ensureExamExecution：创建计划专属考试，返回 AssessmentExecution ID 而不是来源 examId")
  void ensureExamExecution_returnsAssessmentExecutionId() {
    AssessmentExecution execution = new AssessmentExecution();
    execution.setAssessmentExecutionId(777L);
    execution.setExamId(901L);
    execution.setAssessmentKind("QUIZ");
    stubNewExamPublish(901L, execution);

    TrainingExamExecutionCommand command = examCommand();

    Long executionId = adapter.ensureExamExecution(command);

    ArgumentCaptor<Exam> examCaptor = ArgumentCaptor.forClass(Exam.class);
    verify(examMapper).insert(examCaptor.capture());
    assertThat(examCaptor.getValue().getPlanContentId()).isEqualTo(301L);
    assertThat(examCaptor.getValue().getPaperVersionId()).isEqualTo(80L);
    assertThat(examCaptor.getValue().getExamId()).isNotEqualTo(900L);
    assertThat(examCaptor.getValue().getPublishStatus()).isEqualTo("DRAFT");
    assertThat(executionId).isEqualTo(777L);
    assertThat(executionId).isNotEqualTo(900L);
    verify(examService).publish(eq(901L), argThat(extra ->
                                                    "QUIZ".equals(extra.get("assessmentKind"))
                                                      && Long.valueOf(301L).equals(extra.get("trainingPlanContentId"))));
    verify(examMapper, never()).updateById(any(Exam.class));
  }

  @Test
  @DisplayName("ensureExamExecution：计划专属考试使用命令中的冻结策略，不读取来源当前时长和及格线")
  void ensureExamExecution_usesCommandSnapshotNotLiveSource() {
    AssessmentExecution execution = new AssessmentExecution();
    execution.setAssessmentExecutionId(777L);
    stubNewExamPublish(901L, execution);

    TrainingExamExecutionCommand command = examCommand();
    command.setAssessmentKind("FORMAL_EXAM");
    command.setDurationSeconds(3600);
    command.setPassScore(new BigDecimal("60"));
    command.setQuestionOrderMode("IMPORT");
    command.setMaxAttempts(1);

    adapter.ensureExamExecution(command);

    ArgumentCaptor<Exam> examCaptor = ArgumentCaptor.forClass(Exam.class);
    verify(examMapper).insert(examCaptor.capture());
    assertThat(examCaptor.getValue().getDurationSeconds()).isEqualTo(3600);
    assertThat(examCaptor.getValue().getPassScore()).isEqualByComparingTo("60");
    assertThat(examCaptor.getValue().getQuestionOrderMode()).isEqualTo("IMPORT");
    verify(examPolicyMapper).insert(any(ExamPolicy.class));
    verify(examService).publish(eq(901L), argThat(extra -> "FORMAL_EXAM".equals(extra.get("assessmentKind"))));
    verify(examMapper, never()).selectById(any());
    verify(examMapper, never()).updateById(any(Exam.class));
  }

  @Test
  @DisplayName("ensureExamExecution：计划专属策略回填快照中的补考起止时间")
  void ensureExamExecution_copiesRetakeWindowFromSnapshot() {
    AssessmentExecution execution = new AssessmentExecution();
    execution.setAssessmentExecutionId(777L);
    stubNewExamPublish(901L, execution);

    TrainingExamExecutionCommand command = examCommand();
    command.setAssessmentKind("FORMAL_EXAM");
    com.sts.training.dto.TrainingExamPolicySnapshot policy = new com.sts.training.dto.TrainingExamPolicySnapshot();
    policy.setIsRetakeEnabled(1);
    policy.setRetakeMaxCount(1);
    policy.setRetakeStartTime("2026-09-01T08:00:00");
    policy.setRetakeEndTime("2026-09-30T18:00:00");
    command.setPolicy(policy);

    adapter.ensureExamExecution(command);

    ArgumentCaptor<ExamPolicy> policyCaptor = ArgumentCaptor.forClass(ExamPolicy.class);
    verify(examPolicyMapper).insert(policyCaptor.capture());
    assertThat(policyCaptor.getValue().getRetakeStartTime()).isEqualTo(LocalDateTime.of(2026, 9, 1, 8, 0, 0));
    assertThat(policyCaptor.getValue().getRetakeEndTime()).isEqualTo(LocalDateTime.of(2026, 9, 30, 18, 0, 0));
    assertThat(policyCaptor.getValue().getIsRetakeEnabled()).isEqualTo(1);
  }

  @Test
  @DisplayName("ensureExamExecution：创建计划专属 Reviewer 并写入快照指纹")
  void ensureExamExecution_createsPlanScopedReviewersAndFingerprint() {
    AssessmentExecution execution = new AssessmentExecution();
    execution.setAssessmentExecutionId(777L);
    stubNewExamPublish(901L, execution);

    TrainingExamExecutionCommand command = examCommand();
    command.setAssessmentKind("FORMAL_EXAM");
    command.setResourceSnapshotHash("abc123");
    com.sts.training.dto.TrainingExamReviewerSnapshot reviewer = new com.sts.training.dto.TrainingExamReviewerSnapshot();
    reviewer.setReviewerUserId(7L);
    reviewer.setAssignmentSource("USER");
    command.setReviewers(java.util.List.of(reviewer));

    adapter.ensureExamExecution(command);

    verify(examMapper, never()).selectById(any());
    verify(examService).publish(eq(901L), argThat(extra ->
                                                    Long.valueOf(301L).equals(extra.get("trainingPlanContentId"))
                                                      && "abc123".equals(extra.get("trainingResourceSnapshotHash"))
                                                      && "FORMAL_EXAM".equals(extra.get("assessmentKind"))));
    ArgumentCaptor<com.sts.exam.entity.ExamReviewer> reviewerCaptor =
      ArgumentCaptor.forClass(com.sts.exam.entity.ExamReviewer.class);
    verify(examReviewerMapper).insert(reviewerCaptor.capture());
    assertThat(reviewerCaptor.getValue().getAssessmentExecutionId()).isEqualTo(777L);
    assertThat(reviewerCaptor.getValue().getReviewerUserId()).isEqualTo(7L);
  }

  @Test
  @DisplayName("ensureExamExecution：既有执行指纹或批阅人不一致时冲突且不覆盖")
  void ensureExamExecution_conflictsWhenFingerprintDiffers() {
    Exam existing = new Exam();
    existing.setExamId(901L);
    existing.setPlanContentId(301L);
    when(examMapper.selectOne(any())).thenReturn(existing);
    AssessmentExecution execution = new AssessmentExecution();
    execution.setAssessmentExecutionId(777L);
    execution.setPolicySnapshotJson("{\"trainingPlanContentId\":301,\"trainingResourceSnapshotHash\":\"old-hash\"}");
    when(assessmentExecutionService.findStandaloneByExamId(901L)).thenReturn(execution);
    when(examReviewerMapper.selectList(any())).thenReturn(java.util.List.of());

    TrainingExamExecutionCommand command = examCommand();
    command.setResourceSnapshotHash("new-hash");

    org.assertj.core.api.Assertions.assertThatThrownBy(() -> adapter.ensureExamExecution(command))
      .isInstanceOf(com.sts.common.exception.BusinessException.class)
      .hasFieldOrPropertyWithValue("code", 8142)
      .hasMessageContaining("301");
    verify(examMapper, never()).updateById(any(Exam.class));
    verify(examReviewerMapper, never()).insert(any(com.sts.exam.entity.ExamReviewer.class));
  }

  @Test
  @DisplayName("ensureExamExecution：QUESTION_BANK 创建 BANK_PRACTICE")
  void ensureExamExecution_questionBankUsesBankPractice() {
    AssessmentExecution execution = new AssessmentExecution();
    execution.setAssessmentExecutionId(555L);
    stubNewExamPublish(902L, execution);

    TrainingExamExecutionCommand command = new TrainingExamExecutionCommand();
    command.setPlanContentId(302L);
    command.setContentType("QUESTION_BANK");
    command.setAssessmentKind("BANK_PRACTICE");
    command.setExamName("题库练习");
    command.setQuestionBankId(70L);
    command.setDurationSeconds(600);
    command.setPassScore(new BigDecimal("60"));
    command.setMaxAttempts(2);
    command.setOrganizationId(1L);
    command.setQuestionPoolHash("abc");
    command.setQuestionPool(java.util.List.of(new com.sts.training.port.TrainingQuestionPoolItem()));

    Long executionId = adapter.ensureExamExecution(command);

    ArgumentCaptor<Exam> examCaptor = ArgumentCaptor.forClass(Exam.class);
    verify(examMapper).insert(examCaptor.capture());
    assertThat(examCaptor.getValue().getExamType()).isEqualTo("BANK_PRACTICE");
    assertThat(examCaptor.getValue().getQuestionBankId()).isEqualTo(70L);
    assertThat(examCaptor.getValue().getPublishStatus()).isEqualTo("DRAFT");
    assertThat(executionId).isEqualTo(555L);
    verify(questionPoolService).ensure(eq(555L), eq(302L), eq(70L), eq("abc"), anyList());
    verify(examService).publish(eq(902L), argThat(extra -> "BANK_PRACTICE".equals(extra.get("assessmentKind"))));
    verify(examMapper, never()).updateById(any(Exam.class));
  }

  @Test
  @DisplayName("readResult：新版培训练习完成态不伪造成绩")
  void readResult_completedPracticeHasCompletionTimeButNoScore() {
    ExamCandidate candidate = new ExamCandidate();
    candidate.setExamCandidateId(8L);
    candidate.setCandidateStatus("COMPLETED");
    candidate.setFinalAttemptId(9L);
    candidate.setFinalScore(null);
    candidate.setResultEffectiveTime(LocalDateTime.of(2026, 9, 11, 15, 0));
    when(examCandidateMapper.selectOne(any())).thenReturn(candidate);
    com.sts.exam.entity.ExamAttempt attempt = new com.sts.exam.entity.ExamAttempt();
    attempt.setSourceType("TRAINING_PRACTICE");
    attempt.setEvaluatedCount(8);
    attempt.setCorrectCount(6);
    attempt.setWrongCount(2);
    attempt.setUnassessedCount(2);
    attempt.setAccuracyRate(new BigDecimal("75.00"));
    when(examAttemptMapper.selectById(9L)).thenReturn(attempt);

    var result = adapter.readResult(555L, 2L);

    assertThat(result.getSourceStatus()).isEqualTo("COMPLETED");
    assertThat(result.getScore()).isNull();
    assertThat(result.getCompletedTime()).isEqualTo(candidate.getResultEffectiveTime());
    assertThat(result.getEvaluatedCount()).isEqualTo(8);
    assertThat(result.getCorrectCount()).isEqualTo(6);
    assertThat(result.getWrongCount()).isEqualTo(2);
    assertThat(result.getUnassessedCount()).isEqualTo(2);
    assertThat(result.getAccuracyRate()).isEqualByComparingTo("75.00");
  }

  private TrainingExamExecutionCommand examCommand() {
    TrainingExamExecutionCommand command = new TrainingExamExecutionCommand();
    command.setPlanContentId(301L);
    command.setContentType("EXAM");
    command.setAssessmentKind("QUIZ");
    command.setExamName("测验");
    command.setSourceExamId(900L);
    command.setSourceExamCode("E-1");
    command.setPaperVersionId(80L);
    command.setOrganizationId(1L);
    command.setDurationSeconds(1800);
    command.setPassScore(new BigDecimal("60"));
    command.setPassRatio(new BigDecimal("0.6"));
    command.setQuestionOrderMode("IMPORT");
    command.setMultiSelectScoring("ALL_OR_NOTHING");
    command.setResponsibleUserId(7L);
    command.setIsNoticeEnabled(0);
    command.setCredit(BigDecimal.ZERO);
    command.setResourceSnapshotHash("abc123");
    return command;
  }
}

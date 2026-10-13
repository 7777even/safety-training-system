package com.sts.exam.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sts.common.exception.BusinessException;
import com.sts.course.entity.CourseOffering;
import com.sts.course.snapshot.ChapterSnapshot;
import com.sts.course.snapshot.CourseContentSnapshot;
import com.sts.course.snapshot.ItemSnapshot;
import com.sts.exam.entity.AssessmentExecution;
import com.sts.exam.entity.Exam;
import com.sts.exam.entity.ExamCandidate;
import com.sts.exam.mapper.*;
import com.sts.paper.entity.Paper;
import com.sts.paper.entity.PaperVersion;
import com.sts.paper.mapper.PaperMapper;
import com.sts.paper.mapper.PaperVersionMapper;
import com.sts.question.entity.QuestionBank;
import com.sts.question.mapper.QuestionBankMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AssessmentExecutionServiceImplTest {

  @Test
  void ensureCourseExecutions_createsSeparatedQuizAndBankExecutions() {
    AssessmentExecutionMapper executionMapper = mock(AssessmentExecutionMapper.class);
    PaperVersionMapper paperVersionMapper = mock(PaperVersionMapper.class);
    PaperMapper paperMapper = mock(PaperMapper.class);
    List<AssessmentExecution> inserted = new ArrayList<>();
    when(executionMapper.selectOne(any())).thenReturn(null);
    when(executionMapper.insert(any(AssessmentExecution.class))).thenAnswer(invocation -> {
      AssessmentExecution execution = invocation.getArgument(0);
      execution.setAssessmentExecutionId((long) inserted.size() + 1);
      inserted.add(execution);
      return 1;
    });
    PaperVersion version = new PaperVersion();
    version.setPaperVersionId(31L);
    version.setPaperId(41L);
    version.setPublishedStatus("PUBLISHED");
    version.setDurationSeconds(1800);
    version.setTotalScore(new BigDecimal("100"));
    version.setPassScore(new BigDecimal("60"));
    version.setQuestionOrderMode("IMPORT");
    version.setMultiSelectScoring("ALL_OR_NOTHING");
    when(paperVersionMapper.selectById(31L)).thenReturn(version);
    Paper paper = new Paper();
    paper.setPaperName("课程测验卷");
    when(paperMapper.selectById(41L)).thenReturn(paper);

    var frozenKnowledge=mock(CourseKnowledgeSnapshotService.class);
    when(frozenKnowledge.freeze(31L,null)).thenReturn(List.of(new CourseKnowledgeSnapshotService.Snapshot(5L,9L,List.of(7L))));
    AssessmentExecutionService service = new AssessmentExecutionServiceImpl(
      executionMapper, mock(ExamPolicyMapper.class), paperVersionMapper, paperMapper,
      mock(QuestionBankMapper.class), new ObjectMapper(), codec(), mock(ExamCandidateMapper.class),
      mock(ExamReviewerMapper.class), mock(ExamAttemptMapper.class),
      mock(AssessmentExecutionQuestionPoolMapper.class),
      mock(com.sts.course.mapper.CourseAssessmentResultMapper.class), mock(ExamCandidateDeleteHelper.class),
      mock(AssessmentExecutionDeleteHelper.class), frozenKnowledge);

    ItemSnapshot quiz = new ItemSnapshot();
    quiz.setCourseItemId(101L);
    quiz.setItemType("QUIZ");
    quiz.setPaperVersionId(31L);
    ItemSnapshot bank = new ItemSnapshot();
    bank.setCourseItemId(102L);
    bank.setItemType("QUESTION_BANK");
    bank.setQuestionBankId(32L);
    bank.setQuestionBankName("安全基础题库");
    ChapterSnapshot chapter = new ChapterSnapshot();
    chapter.setItems(List.of(quiz, bank));
    CourseContentSnapshot snapshot = new CourseContentSnapshot();
    snapshot.setChapters(List.of(chapter));
    CourseOffering offering = new CourseOffering();
    offering.setCourseOfferingId(11L);
    offering.setCourseVersionId(12L);
    offering.setOrganizationId(1L);

    List<AssessmentExecution> result = service.ensureCourseExecutions(offering, snapshot);

    assertThat(result).hasSize(2);
    assertThat(inserted).extracting(AssessmentExecution::getSourceType)
      .containsExactly("COURSE_QUIZ", "COURSE_BANK_PRACTICE");
    assertThat(inserted.get(0).getResourceId()).isEqualTo(31L);
    assertThat(inserted.get(1).getResourceId()).isEqualTo(32L);
    assertThat(inserted.get(1).getResourceType()).isEqualTo("QUESTION_BANK");
    assertThat(inserted.get(1).getPolicySnapshotJson()).contains("ANSWER_ALL");
    assertThat(inserted.get(0).getPolicySnapshotJson()).contains("knowledgePointSnapshots","questionVersionId","knowledgePointIds");
    verify(frozenKnowledge).freeze(31L,null);
    verify(frozenKnowledge).freeze(null,32L);
  }

  @Test
  void refreshStandalone_updatesUnusedExecutionInPlace() {
    AssessmentExecutionMapper executionMapper = mock(AssessmentExecutionMapper.class);
    ExamPolicyMapper policyMapper = mock(ExamPolicyMapper.class);
    PaperVersionMapper paperVersionMapper = mock(PaperVersionMapper.class);
    PaperMapper paperMapper = mock(PaperMapper.class);
    AssessmentExecution existing = standaloneExecution(11L, 1L);
    when(executionMapper.selectOne(any())).thenReturn(existing);
    when(executionMapper.updateById(any(AssessmentExecution.class))).thenReturn(1);
    PaperVersion version = new PaperVersion();
    version.setPaperVersionId(40L);
    version.setPaperId(8L);
    version.setPublishedStatus("PUBLISHED");
    version.setDurationSeconds(3600);
    version.setTotalScore(new BigDecimal("100.00"));
    version.setPassScore(new BigDecimal("60.00"));
    version.setQuestionOrderMode("RANDOM");
    version.setMultiSelectScoring("PARTIAL");
    when(paperVersionMapper.selectById(40L)).thenReturn(version);
    Paper paper = new Paper();
    paper.setPaperId(8L);
    paper.setPaperName("最终试卷");
    when(paperMapper.selectById(8L)).thenReturn(paper);
    AssessmentExecutionDeleteHelper executionDeleteHelper = mock(AssessmentExecutionDeleteHelper.class);
    when(executionDeleteHelper.removeById(11L)).thenReturn(true);
    AssessmentExecutionService service = new AssessmentExecutionServiceImpl(
      executionMapper, policyMapper, paperVersionMapper, paperMapper, mock(QuestionBankMapper.class),
      new ObjectMapper(), codec(), mock(ExamCandidateMapper.class), mock(ExamReviewerMapper.class),
      mock(ExamAttemptMapper.class), mock(AssessmentExecutionQuestionPoolMapper.class),
      mock(com.sts.course.mapper.CourseAssessmentResultMapper.class), mock(ExamCandidateDeleteHelper.class),
      executionDeleteHelper, org.mockito.Mockito.mock(com.sts.exam.service.CourseKnowledgeSnapshotService.class));
    Exam exam = new Exam();
    exam.setExamId(1L);
    exam.setOrganizationId(2L);
    exam.setExamType("ONLINE");
    exam.setPaperVersionId(40L);
    exam.setDurationSeconds(1800);
    exam.setPassScore(new BigDecimal("55.00"));
    exam.setQuestionOrderMode("IMPORT");
    exam.setMultiSelectScoring("ALL_OR_NOTHING");

    AssessmentExecution refreshed = service.refreshStandalone(exam);

    assertThat(refreshed.getAssessmentExecutionId()).isEqualTo(11L);
    assertThat(refreshed.getResourceId()).isEqualTo(40L);
    assertThat(refreshed.getResourceNameSnapshot()).isEqualTo("最终试卷");
    assertThat(refreshed.getDurationSecondsSnapshot()).isEqualTo(1800);
    assertThat(refreshed.getPassScoreSnapshot()).isEqualByComparingTo("55.00");
    assertThat(refreshed.getSnapshotHash()).hasSize(64).isNotEqualTo("old");
    assertThat(refreshed.getSourceType()).isEqualTo("STANDALONE_EXAM");
    verify(executionMapper).updateById(any(AssessmentExecution.class));
    verify(executionMapper, never()).insert(any(AssessmentExecution.class));
  }

  @Test
  @DisplayName("线下考试及格分高于 100 时执行快照满分同步上移")
  void ensureStandalone_offlineHighPassScoreRaisesTotalScore() {
    AssessmentExecutionMapper executionMapper = mock(AssessmentExecutionMapper.class);
    when(executionMapper.selectOne(any())).thenReturn(null);
    when(executionMapper.insert(any(AssessmentExecution.class))).thenReturn(1);
    AssessmentExecutionService service = new AssessmentExecutionServiceImpl(
      executionMapper, mock(ExamPolicyMapper.class), mock(PaperVersionMapper.class), mock(PaperMapper.class),
      mock(QuestionBankMapper.class), new ObjectMapper(), codec(), mock(ExamCandidateMapper.class),
      mock(ExamReviewerMapper.class), mock(ExamAttemptMapper.class), mock(AssessmentExecutionQuestionPoolMapper.class),
      mock(com.sts.course.mapper.CourseAssessmentResultMapper.class), mock(ExamCandidateDeleteHelper.class),
      mock(AssessmentExecutionDeleteHelper.class), org.mockito.Mockito.mock(com.sts.exam.service.CourseKnowledgeSnapshotService.class));
    Exam exam = new Exam();
    exam.setExamId(88L);
    exam.setOrganizationId(1L);
    exam.setExamType("OFFLINE");
    exam.setExamName("高分线下考试");
    exam.setDurationSeconds(0);
    exam.setPassScore(new BigDecimal("120"));
    exam.setPassRatio(BigDecimal.ONE);
    exam.setQuestionOrderMode("NOT_APPLICABLE");
    exam.setMultiSelectScoring("NOT_APPLICABLE");

    AssessmentExecution execution = service.ensureStandalone(exam);

    assertThat(execution.getTotalScoreSnapshot()).isEqualByComparingTo("120");
    assertThat(execution.getPassScoreSnapshot()).isEqualByComparingTo("120");
  }

  @Test
  void refreshStandalone_preservesConfiguredParticipantsBeforePublish() {
    AssessmentExecutionMapper executionMapper = mock(AssessmentExecutionMapper.class);
    ExamCandidateMapper candidateMapper = mock(ExamCandidateMapper.class);
    ExamReviewerMapper reviewerMapper = mock(ExamReviewerMapper.class);
    AssessmentExecution existing = standaloneExecution(11L, 1L);
    when(executionMapper.selectOne(any())).thenReturn(existing);
    when(executionMapper.updateById(any(AssessmentExecution.class))).thenReturn(1);
    when(candidateMapper.selectCount(any())).thenReturn(2L);
    when(reviewerMapper.selectCount(any())).thenReturn(1L);
    PaperVersion version = new PaperVersion();
    version.setPaperVersionId(40L);
    version.setPaperId(8L);
    version.setPublishedStatus("PUBLISHED");
    version.setDurationSeconds(3600);
    version.setTotalScore(new BigDecimal("100.00"));
    version.setPassScore(new BigDecimal("60.00"));
    version.setQuestionOrderMode("RANDOM");
    version.setMultiSelectScoring("PARTIAL");
    PaperVersionMapper paperVersionMapper = mock(PaperVersionMapper.class);
    when(paperVersionMapper.selectById(40L)).thenReturn(version);
    Paper paper = new Paper();
    paper.setPaperId(8L);
    paper.setPaperName("最终试卷");
    PaperMapper paperMapper = mock(PaperMapper.class);
    when(paperMapper.selectById(8L)).thenReturn(paper);
    AssessmentExecutionService service = new AssessmentExecutionServiceImpl(
      executionMapper, mock(ExamPolicyMapper.class), paperVersionMapper, paperMapper,
      mock(QuestionBankMapper.class), new ObjectMapper(), codec(), candidateMapper, reviewerMapper,
      mock(ExamAttemptMapper.class), mock(AssessmentExecutionQuestionPoolMapper.class),
      mock(com.sts.course.mapper.CourseAssessmentResultMapper.class), mock(ExamCandidateDeleteHelper.class),
      mock(AssessmentExecutionDeleteHelper.class), org.mockito.Mockito.mock(com.sts.exam.service.CourseKnowledgeSnapshotService.class));
    Exam exam = new Exam();
    exam.setExamId(1L);
    exam.setOrganizationId(2L);
    exam.setExamType("ONLINE");
    exam.setPaperVersionId(40L);
    exam.setDurationSeconds(1800);
    exam.setPassScore(new BigDecimal("55.00"));
    exam.setQuestionOrderMode("IMPORT");
    exam.setMultiSelectScoring("ALL_OR_NOTHING");

    AssessmentExecution refreshed = service.refreshStandalone(exam);

    assertThat(refreshed.getAssessmentExecutionId()).isEqualTo(11L);
    assertThat(refreshed.getResourceId()).isEqualTo(40L);
    verify(executionMapper).updateById(any(AssessmentExecution.class));
  }

  @Test
  void refreshStandalone_rejectsExecutionWithFrozenQuestionPool() {
    AssessmentExecutionMapper executionMapper = mock(AssessmentExecutionMapper.class);
    PaperVersionMapper paperVersionMapper = mock(PaperVersionMapper.class);
    PaperMapper paperMapper = mock(PaperMapper.class);
    AssessmentExecutionQuestionPoolMapper poolMapper = mock(AssessmentExecutionQuestionPoolMapper.class);
    when(executionMapper.selectOne(any())).thenReturn(standaloneExecution(11L, 1L));
    when(poolMapper.selectCount(any())).thenReturn(1L);
    PaperVersion version = new PaperVersion();
    version.setPaperVersionId(40L);
    version.setPaperId(8L);
    version.setPublishedStatus("PUBLISHED");
    version.setDurationSeconds(1800);
    version.setTotalScore(new BigDecimal("100"));
    version.setPassScore(new BigDecimal("60"));
    version.setQuestionOrderMode("IMPORT");
    version.setMultiSelectScoring("ALL_OR_NOTHING");
    when(paperVersionMapper.selectById(40L)).thenReturn(version);
    Paper paper = new Paper();
    paper.setPaperName("试卷");
    when(paperMapper.selectById(8L)).thenReturn(paper);
    AssessmentExecutionService service = new AssessmentExecutionServiceImpl(
      executionMapper, mock(ExamPolicyMapper.class), paperVersionMapper, paperMapper,
      mock(QuestionBankMapper.class), new ObjectMapper(), codec(), mock(ExamCandidateMapper.class),
      mock(ExamReviewerMapper.class), mock(ExamAttemptMapper.class), poolMapper,
      mock(com.sts.course.mapper.CourseAssessmentResultMapper.class), mock(ExamCandidateDeleteHelper.class),
      mock(AssessmentExecutionDeleteHelper.class), org.mockito.Mockito.mock(com.sts.exam.service.CourseKnowledgeSnapshotService.class));
    Exam exam = new Exam();
    exam.setExamId(1L);
    exam.setOrganizationId(2L);
    exam.setExamType("ONLINE");
    exam.setPaperVersionId(40L);
    exam.setDurationSeconds(1800);
    exam.setPassScore(new BigDecimal("60"));
    exam.setQuestionOrderMode("IMPORT");
    exam.setMultiSelectScoring("ALL_OR_NOTHING");

    assertThatThrownBy(() -> service.refreshStandalone(exam))
      .isInstanceOfSatisfying(BusinessException.class, ex -> {
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ex.getCode()).isEqualTo(7003);
      });
    verify(executionMapper, never()).insert(any(AssessmentExecution.class));
  }

  @Test
  void deleteStandaloneIfUnused_removesReviewersThenCandidatesAndExecution() {
    AssessmentExecutionMapper executionMapper = mock(AssessmentExecutionMapper.class);
    ExamCandidateMapper candidateMapper = mock(ExamCandidateMapper.class);
    ExamReviewerMapper reviewerMapper = mock(ExamReviewerMapper.class);
    ExamAttemptMapper attemptMapper = mock(ExamAttemptMapper.class);
    ExamCandidateDeleteHelper candidateDeleteHelper = mock(ExamCandidateDeleteHelper.class);
    AssessmentExecutionDeleteHelper executionDeleteHelper = mock(AssessmentExecutionDeleteHelper.class);
    AssessmentExecution execution = standaloneExecution(11L, 1L);
    when(executionMapper.selectOne(any())).thenReturn(execution);
    when(attemptMapper.selectCount(any())).thenReturn(0L);
    ExamCandidate candidate = new ExamCandidate();
    candidate.setExamCandidateId(21L);
    when(candidateMapper.selectList(any())).thenReturn(List.of(candidate));
    AssessmentExecutionService service = new AssessmentExecutionServiceImpl(
      executionMapper, mock(ExamPolicyMapper.class), mock(PaperVersionMapper.class), mock(PaperMapper.class),
      mock(QuestionBankMapper.class), new ObjectMapper(), codec(), candidateMapper, reviewerMapper, attemptMapper,
      mock(AssessmentExecutionQuestionPoolMapper.class),
      mock(com.sts.course.mapper.CourseAssessmentResultMapper.class),
      candidateDeleteHelper, executionDeleteHelper, org.mockito.Mockito.mock(com.sts.exam.service.CourseKnowledgeSnapshotService.class));

    service.deleteStandaloneIfUnused(1L);

    verify(reviewerMapper).delete(any());
    verify(candidateDeleteHelper).removeById(21L);
    verify(executionDeleteHelper).removeById(11L);
  }

  @Test
  void deleteStandaloneIfUnused_rejectsExecutionWithAttempts() {
    AssessmentExecutionMapper executionMapper = mock(AssessmentExecutionMapper.class);
    ExamCandidateMapper candidateMapper = mock(ExamCandidateMapper.class);
    ExamReviewerMapper reviewerMapper = mock(ExamReviewerMapper.class);
    ExamAttemptMapper attemptMapper = mock(ExamAttemptMapper.class);
    AssessmentExecution execution = standaloneExecution(11L, 1L);
    when(executionMapper.selectOne(any())).thenReturn(execution);
    when(attemptMapper.selectCount(any())).thenReturn(1L);
    AssessmentExecutionService service = new AssessmentExecutionServiceImpl(
      executionMapper, mock(ExamPolicyMapper.class), mock(PaperVersionMapper.class), mock(PaperMapper.class),
      mock(QuestionBankMapper.class), new ObjectMapper(), codec(), candidateMapper, reviewerMapper, attemptMapper,
      mock(AssessmentExecutionQuestionPoolMapper.class),
      mock(com.sts.course.mapper.CourseAssessmentResultMapper.class),
      mock(ExamCandidateDeleteHelper.class), mock(AssessmentExecutionDeleteHelper.class), org.mockito.Mockito.mock(com.sts.exam.service.CourseKnowledgeSnapshotService.class));

    assertThatThrownBy(() -> service.deleteStandaloneIfUnused(1L))
      .isInstanceOfSatisfying(BusinessException.class, ex -> {
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ex.getCode()).isEqualTo(7003);
      });
    verifyNoInteractions(reviewerMapper);
    verify(candidateMapper, never()).selectList(any());
  }

  @Test
  void refreshStandalone_planContentWritesTrainingSourceTypeAndKind() {
    AssessmentExecutionMapper executionMapper = mock(AssessmentExecutionMapper.class);
    PaperVersionMapper paperVersionMapper = mock(PaperVersionMapper.class);
    PaperMapper paperMapper = mock(PaperMapper.class);
    when(executionMapper.selectOne(any())).thenReturn(null);
    when(executionMapper.insert(any(AssessmentExecution.class))).thenAnswer(invocation -> {
      AssessmentExecution execution = invocation.getArgument(0);
      execution.setAssessmentExecutionId(88L);
      return 1;
    });
    PaperVersion version = new PaperVersion();
    version.setPaperVersionId(40L);
    version.setPaperId(8L);
    version.setPublishedStatus("PUBLISHED");
    version.setDurationSeconds(1800);
    version.setTotalScore(new BigDecimal("100"));
    version.setPassScore(new BigDecimal("60"));
    version.setQuestionOrderMode("IMPORT");
    version.setMultiSelectScoring("ALL_OR_NOTHING");
    when(paperVersionMapper.selectById(40L)).thenReturn(version);
    Paper paper = new Paper();
    paper.setPaperName("测验卷");
    when(paperMapper.selectById(8L)).thenReturn(paper);
    AssessmentExecutionService service = new AssessmentExecutionServiceImpl(
      executionMapper, mock(ExamPolicyMapper.class), paperVersionMapper, paperMapper,
      mock(QuestionBankMapper.class), new ObjectMapper(), codec(), mock(ExamCandidateMapper.class),
      mock(ExamReviewerMapper.class), mock(ExamAttemptMapper.class),
      mock(AssessmentExecutionQuestionPoolMapper.class),
      mock(com.sts.course.mapper.CourseAssessmentResultMapper.class),
      mock(ExamCandidateDeleteHelper.class), mock(AssessmentExecutionDeleteHelper.class), org.mockito.Mockito.mock(com.sts.exam.service.CourseKnowledgeSnapshotService.class));
    Exam exam = new Exam();
    exam.setExamId(1L);
    exam.setOrganizationId(2L);
    exam.setExamType("ONLINE");
    exam.setPlanContentId(301L);
    exam.setPaperVersionId(40L);
    exam.setDurationSeconds(1800);
    exam.setPassScore(new BigDecimal("60"));
    exam.setQuestionOrderMode("IMPORT");
    exam.setMultiSelectScoring("ALL_OR_NOTHING");

    AssessmentExecution created = service.refreshStandalone(exam, java.util.Map.of(
      "assessmentKind", "QUIZ", "trainingPlanContentId", 301L));

    assertThat(created.getSourceType()).isEqualTo("TRAINING_EXAM");
    assertThat(created.getAssessmentKind()).isEqualTo("QUIZ");
    assertThat(created.getAssessmentExecutionId()).isEqualTo(88L);
    assertThat(created.getPolicySnapshotJson()).contains("trainingPlanContentId");
  }

  @Test
  void refreshStandalone_bankPracticeWithPlanContentWritesTrainingPractice() {
    AssessmentExecutionMapper executionMapper = mock(AssessmentExecutionMapper.class);
    QuestionBankMapper questionBankMapper = mock(QuestionBankMapper.class);
    when(executionMapper.selectOne(any())).thenReturn(null);
    when(executionMapper.insert(any(AssessmentExecution.class))).thenAnswer(invocation -> {
      AssessmentExecution execution = invocation.getArgument(0);
      execution.setAssessmentExecutionId(55L);
      return 1;
    });
    QuestionBank bank = new QuestionBank();
    bank.setBankName("安全题库");
    when(questionBankMapper.selectById(70L)).thenReturn(bank);
    AssessmentExecutionService service = new AssessmentExecutionServiceImpl(
      executionMapper, mock(ExamPolicyMapper.class), mock(PaperVersionMapper.class), mock(PaperMapper.class),
      questionBankMapper, new ObjectMapper(), codec(), mock(ExamCandidateMapper.class),
      mock(ExamReviewerMapper.class), mock(ExamAttemptMapper.class),
      mock(AssessmentExecutionQuestionPoolMapper.class),
      mock(com.sts.course.mapper.CourseAssessmentResultMapper.class),
      mock(ExamCandidateDeleteHelper.class), mock(AssessmentExecutionDeleteHelper.class), org.mockito.Mockito.mock(com.sts.exam.service.CourseKnowledgeSnapshotService.class));
    Exam exam = new Exam();
    exam.setExamId(2L);
    exam.setOrganizationId(2L);
    exam.setExamType("BANK_PRACTICE");
    exam.setPlanContentId(302L);
    exam.setQuestionBankId(70L);
    exam.setDurationSeconds(600);
    exam.setPassScore(new BigDecimal("60"));
    exam.setQuestionOrderMode("RANDOM");
    exam.setMultiSelectScoring("ALL_OR_NOTHING");

    AssessmentExecution created = service.refreshStandalone(exam, java.util.Map.of("maxAttempts", 2));

    assertThat(created.getSourceType()).isEqualTo("TRAINING_PRACTICE");
    assertThat(created.getAssessmentKind()).isEqualTo("BANK_PRACTICE");
    assertThat(created.getPolicySnapshotJson()).contains("maxAttempts");
  }

  @DisplayName("BANK_PRACTICE：独立题库练习不冻结分数和及格线")
  @Test
  void refreshStandalone_bankPracticeComputesTotalScoreFromDrawRules() {
    AssessmentExecutionMapper executionMapper = mock(AssessmentExecutionMapper.class);
    QuestionBankMapper questionBankMapper = mock(QuestionBankMapper.class);
    when(executionMapper.selectOne(any())).thenReturn(null);
    when(executionMapper.insert(any(AssessmentExecution.class))).thenAnswer(invocation -> {
      AssessmentExecution execution = invocation.getArgument(0);
      execution.setAssessmentExecutionId(56L);
      return 1;
    });
    QuestionBank bank = new QuestionBank();
    bank.setBankName("安全题库");
    when(questionBankMapper.selectById(70L)).thenReturn(bank);
    AssessmentExecutionService service = new AssessmentExecutionServiceImpl(
      executionMapper, mock(ExamPolicyMapper.class), mock(PaperVersionMapper.class), mock(PaperMapper.class),
      questionBankMapper, new ObjectMapper(), codec(), mock(ExamCandidateMapper.class),
      mock(ExamReviewerMapper.class), mock(ExamAttemptMapper.class),
      mock(AssessmentExecutionQuestionPoolMapper.class),
      mock(com.sts.course.mapper.CourseAssessmentResultMapper.class),
      mock(ExamCandidateDeleteHelper.class), mock(AssessmentExecutionDeleteHelper.class), org.mockito.Mockito.mock(com.sts.exam.service.CourseKnowledgeSnapshotService.class));
    Exam exam = new Exam();
    exam.setExamId(2L);
    exam.setOrganizationId(2L);
    exam.setExamType("BANK_PRACTICE");
    exam.setQuestionBankId(70L);
    exam.setDurationSeconds(600);
    exam.setPassScore(new BigDecimal("60"));
    exam.setQuestionOrderMode("RANDOM");
    exam.setMultiSelectScoring("ALL_OR_NOTHING");
    // 历史请求仍可携带每题分值，但题库练习统一为不评分语义。
    exam.setDrawRuleJson("""
      [{"questionType":"SINGLE","drawCount":3,"scorePerQuestion":4,"sortOrder":1},
       {"questionType":"MULTIPLE","drawCount":2,"scorePerQuestion":4,"sortOrder":2}]
      """);

    AssessmentExecution created = service.refreshStandalone(exam);

    assertThat(created.getResourceType()).isEqualTo("QUESTION_BANK");
    assertThat(created.getTotalScoreSnapshot()).isNull();
    assertThat(created.getPassScoreSnapshot()).isNull();
  }

  private AssessmentExecution standaloneExecution(Long executionId, Long examId) {
    AssessmentExecution execution = new AssessmentExecution();
    execution.setAssessmentExecutionId(executionId);
    execution.setOrganizationId(2L);
    execution.setSourceType("STANDALONE_EXAM");
    execution.setSourceBusinessId(examId);
    execution.setExamId(examId);
    execution.setAssessmentKind("FORMAL_EXAM");
    execution.setResourceType("PAPER_VERSION");
    execution.setResourceId(30L);
    execution.setResourceNameSnapshot("旧试卷");
    execution.setDurationSecondsSnapshot(1800);
    execution.setTotalScoreSnapshot(new BigDecimal("100"));
    execution.setPassScoreSnapshot(new BigDecimal("60"));
    execution.setQuestionOrderModeSnapshot("IMPORT");
    execution.setMultiSelectScoringSnapshot("ALL_OR_NOTHING");
    execution.setPolicySnapshotJson("{}");
    execution.setExecutionStatus("READY");
    new AssessmentExecutionSnapshotCodec(new ObjectMapper()).encode(execution);
    return execution;
  }

  private AssessmentExecutionSnapshotCodec codec() {
    return new AssessmentExecutionSnapshotCodec(new ObjectMapper());
  }
}

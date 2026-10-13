package com.sts.exam.service;

import cn.dev33.satoken.stp.StpUtil;
import com.sts.common.api.PageRequest;
import com.sts.common.exception.BusinessException;
import com.sts.exam.dto.ExamLearnerTaskQuery;
import com.sts.exam.entity.*;
import com.sts.exam.mapper.*;
import com.sts.exam.vo.ExamAttemptVO;
import com.sts.exam.vo.ExamLearnerTaskVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ExamLearnerServiceImplTest {

  private MockedStatic<StpUtil> stpUtil;

  @AfterEach
  void tearDown() {
    if (stpUtil != null) stpUtil.close();
  }

  @Test
  void pageMyTasks_returnsOnlyCurrentUsersTasks_andPublishedResult() {
    ExamCandidateMapper candidateMapper = mock(ExamCandidateMapper.class);
    ExamMapper examMapper = mock(ExamMapper.class);
    ExamAttemptMapper attemptMapper = mock(ExamAttemptMapper.class);
    ExamPolicyMapper policyMapper = mock(ExamPolicyMapper.class);
    ExamLearnerService service = service(candidateMapper, examMapper, attemptMapper, policyMapper,
                                         mock(ExamAttemptQuestionMapper.class), mock(ExamAttemptOptionMapper.class),
                                         mock(ExamAnswerMapper.class), mock(ExamAnswerItemMapper.class));
    stpUtil = mockStatic(StpUtil.class);
    stpUtil.when(StpUtil::getLoginIdAsLong).thenReturn(7L);

    ExamCandidate candidate = candidate(20L, 1L, "PASSED");
    when(candidateMapper.selectList(any())).thenReturn(List.of(candidate));
    when(examMapper.selectBatchIds(List.of(1L))).thenReturn(List.of(exam(1L)));
    ExamAttempt attempt = attempt(40L, 20L, 7L, "PUBLISHED");
    attempt.setSubmittedTime(LocalDateTime.now().minusMinutes(5));
    attempt.setScorePublishedTime(LocalDateTime.now());
    attempt.setFinalScore(new BigDecimal("88"));
    attempt.setIsPassed(1);
    when(attemptMapper.selectList(any())).thenReturn(List.of(attempt));
    ExamPolicy policy = new ExamPolicy();
    policy.setExamId(1L);
    policy.setIsScoreVisible(1);
    when(policyMapper.selectList(any())).thenReturn(List.of(policy));

    var page = service.pageMyTasks(new PageRequest<>(1, 20, new ExamLearnerTaskQuery()));

    assertThat(page.total()).isEqualTo(1);
    ExamLearnerTaskVO task = page.list().getFirst();
    assertThat(task.getExamCandidateId()).isEqualTo(20L);
    assertThat(task.getCurrentAttemptId()).isEqualTo(40L);
    assertThat(task.getScorePublishStatus()).isEqualTo("PUBLISHED");
    assertThat(task.getFinalScore()).isEqualByComparingTo("88");
    assertThat(task.getAvailableAction()).isEqualTo("VIEW_RESULT");
    assertThat(task.getCoverUrl()).isEqualTo("/api/exam/learner/exams/1/cover");
  }

  @Test
  void pageMyTasks_omitsCoverUrlWhenExamHasNoCover() {
    ExamCandidateMapper candidateMapper = mock(ExamCandidateMapper.class);
    ExamMapper examMapper = mock(ExamMapper.class);
    ExamAttemptMapper attemptMapper = mock(ExamAttemptMapper.class);
    ExamPolicyMapper policyMapper = mock(ExamPolicyMapper.class);
    ExamLearnerService service = service(candidateMapper, examMapper, attemptMapper, policyMapper,
                                         mock(ExamAttemptQuestionMapper.class), mock(ExamAttemptOptionMapper.class),
                                         mock(ExamAnswerMapper.class), mock(ExamAnswerItemMapper.class));
    stpUtil = mockStatic(StpUtil.class);
    stpUtil.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
    when(candidateMapper.selectList(any())).thenReturn(List.of(candidate(20L, 1L, "NOT_STARTED")));
    Exam noCover = exam(1L);
    noCover.setCoverFileId(null);
    when(examMapper.selectBatchIds(List.of(1L))).thenReturn(List.of(noCover));
    when(attemptMapper.selectList(any())).thenReturn(List.of());
    when(policyMapper.selectList(any())).thenReturn(List.of());

    var page = service.pageMyTasks(new PageRequest<>(1, 20, new ExamLearnerTaskQuery()));

    assertThat(page.list().getFirst().getCoverUrl()).isNull();
  }

  @Test
  void pageMyTasks_hidesDraftExamEvenWhenCandidateAndExecutionExist() {
    ExamCandidateMapper candidateMapper = mock(ExamCandidateMapper.class);
    ExamMapper examMapper = mock(ExamMapper.class);
    ExamAttemptMapper attemptMapper = mock(ExamAttemptMapper.class);
    ExamPolicyMapper policyMapper = mock(ExamPolicyMapper.class);
    ExamLearnerService service = service(candidateMapper, examMapper, attemptMapper, policyMapper,
                                         mock(ExamAttemptQuestionMapper.class), mock(ExamAttemptOptionMapper.class),
                                         mock(ExamAnswerMapper.class), mock(ExamAnswerItemMapper.class));
    stpUtil = mockStatic(StpUtil.class);
    stpUtil.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
    when(candidateMapper.selectList(any())).thenReturn(List.of(candidate(20L, 1L, "NOT_STARTED")));
    Exam draft = exam(1L);
    draft.setPublishStatus("DRAFT");
    when(examMapper.selectBatchIds(List.of(1L))).thenReturn(List.of(draft));

    var page = service.pageMyTasks(new PageRequest<>(1, 20, new ExamLearnerTaskQuery()));

    assertThat(page.list()).isEmpty();
    assertThat(page.total()).isZero();
  }

  @Test
  @DisplayName("pageMyTasks：我的考试入口排除培训来源，保留独立考试")
  void pageMyTasks_excludesTrainingSourceKeepsStandalone() {
    ExamCandidateMapper candidateMapper = mock(ExamCandidateMapper.class);
    ExamMapper examMapper = mock(ExamMapper.class);
    ExamAttemptMapper attemptMapper = mock(ExamAttemptMapper.class);
    ExamPolicyMapper policyMapper = mock(ExamPolicyMapper.class);
    AssessmentExecutionService executionService = mock(AssessmentExecutionService.class);
    ExamLearnerService service = new ExamLearnerServiceImpl(
      candidateMapper, examMapper, attemptMapper, policyMapper,
      mock(ExamAttemptQuestionMapper.class), mock(ExamAttemptOptionMapper.class),
      mock(ExamAnswerMapper.class), mock(ExamAnswerItemMapper.class), executionService,
      mock(com.sts.admin.mapper.SysFileInfoMapper.class),
      mock(com.sts.admin.storage.StorageService.class));
    stpUtil = mockStatic(StpUtil.class);
    stpUtil.when(StpUtil::getLoginIdAsLong).thenReturn(7L);

    ExamCandidate standaloneCandidate = candidate(20L, 1L, "NOT_STARTED");
    standaloneCandidate.setAssessmentExecutionId(11L);
    ExamCandidate trainingCandidate = candidate(21L, 2L, "NOT_STARTED");
    trainingCandidate.setAssessmentExecutionId(12L);
    when(candidateMapper.selectList(any())).thenReturn(List.of(standaloneCandidate, trainingCandidate));
    AssessmentExecution standalone = standaloneExecution();
    AssessmentExecution training = new AssessmentExecution();
    training.setAssessmentExecutionId(12L);
    training.setExamId(2L);
    training.setSourceType("TRAINING_EXAM");
    when(executionService.requireById(11L)).thenReturn(standalone);
    when(executionService.requireById(12L)).thenReturn(training);
    Exam standaloneExam = exam(1L);
    Exam trainingExam = exam(2L);
    trainingExam.setExamName("计划专属考试");
    when(examMapper.selectBatchIds(any())).thenReturn(List.of(standaloneExam, trainingExam));
    when(attemptMapper.selectList(any())).thenReturn(List.of());
    when(policyMapper.selectList(any())).thenReturn(List.of());

    var page = service.pageMyTasks(new PageRequest<>(1, 20, new ExamLearnerTaskQuery()));

    assertThat(page.total()).isEqualTo(1);
    assertThat(page.list().getFirst().getExamCandidateId()).isEqualTo(20L);
    assertThat(page.list()).extracting(ExamLearnerTaskVO::getSourceType).containsOnly("STANDALONE_EXAM");
  }

  @Test
  @DisplayName("pageMyTasks：仅培训来源时直接返回空页，不查询独立考试")
  void pageMyTasks_returnsEmptyWhenOnlyTrainingSourcesExist() {
    ExamCandidateMapper candidateMapper = mock(ExamCandidateMapper.class);
    ExamMapper examMapper = mock(ExamMapper.class);
    AssessmentExecutionService executionService = mock(AssessmentExecutionService.class);
    ExamLearnerService service = new ExamLearnerServiceImpl(
      candidateMapper, examMapper, mock(ExamAttemptMapper.class), mock(ExamPolicyMapper.class),
      mock(ExamAttemptQuestionMapper.class), mock(ExamAttemptOptionMapper.class),
      mock(ExamAnswerMapper.class), mock(ExamAnswerItemMapper.class), executionService,
      mock(com.sts.admin.mapper.SysFileInfoMapper.class),
      mock(com.sts.admin.storage.StorageService.class));
    stpUtil = mockStatic(StpUtil.class);
    stpUtil.when(StpUtil::getLoginIdAsLong).thenReturn(7L);

    ExamCandidate candidate = candidate(20L, 1L, "NOT_STARTED");
    candidate.setAssessmentExecutionId(12L);
    AssessmentExecution training = new AssessmentExecution();
    training.setAssessmentExecutionId(12L);
    training.setExamId(2L);
    training.setSourceType("TRAINING_EXAM");
    when(candidateMapper.selectList(any())).thenReturn(List.of(candidate));
    when(executionService.requireById(12L)).thenReturn(training);

    var page = service.pageMyTasks(new PageRequest<>(1, 20, new ExamLearnerTaskQuery()));

    assertThat(page.list()).isEmpty();
    assertThat(page.total()).isZero();
    verifyNoInteractions(examMapper);
  }

  @Test
  @DisplayName("getMyAttempt：培训来源 Attempt 仍可按 ID 读取")
  void getMyAttempt_allowsTrainingExamAttempt() {
    ExamCandidateMapper candidateMapper = mock(ExamCandidateMapper.class);
    ExamMapper examMapper = mock(ExamMapper.class);
    ExamAttemptMapper attemptMapper = mock(ExamAttemptMapper.class);
    ExamPolicyMapper policyMapper = mock(ExamPolicyMapper.class);
    AssessmentExecution training = new AssessmentExecution();
    training.setAssessmentExecutionId(12L);
    training.setExamId(2L);
    training.setSourceType("TRAINING_EXAM");
    ExamLearnerService service = service(candidateMapper, examMapper, attemptMapper, policyMapper,
                                         mock(ExamAttemptQuestionMapper.class), mock(ExamAttemptOptionMapper.class),
                                         mock(ExamAnswerMapper.class), mock(ExamAnswerItemMapper.class), training);
    stpUtil = mockStatic(StpUtil.class);
    stpUtil.when(StpUtil::getLoginIdAsLong).thenReturn(7L);

    ExamAttempt attempt = attempt(40L, 20L, 7L, "ANSWERING");
    attempt.setAssessmentExecutionId(12L);
    attempt.setSourceType("TRAINING_EXAM");
    when(attemptMapper.selectById(40L)).thenReturn(attempt);
    when(candidateMapper.selectById(20L)).thenReturn(candidate(20L, 2L, "IN_PROGRESS"));
    when(examMapper.selectById(2L)).thenReturn(exam(2L));
    when(policyMapper.selectOne(any())).thenReturn(new ExamPolicy());

    ExamAttemptVO result = service.getMyAttempt(40L);

    assertThat(result.getExamAttemptId()).isEqualTo(40L);
    assertThat(result.getSourceType()).isEqualTo("TRAINING_EXAM");
  }

  @Test
  void openCover_returnsExamOwnedFileForAssignedLearner() {
    ExamCandidateMapper candidateMapper = mock(ExamCandidateMapper.class);
    ExamMapper examMapper = mock(ExamMapper.class);
    com.sts.admin.mapper.SysFileInfoMapper fileMapper = mock(com.sts.admin.mapper.SysFileInfoMapper.class);
    com.sts.admin.storage.StorageService storageService = mock(com.sts.admin.storage.StorageService.class);
    AssessmentExecutionService executionService = mock(AssessmentExecutionService.class);
    when(executionService.requireById(any())).thenReturn(standaloneExecution());
    when(executionService.findStandaloneByExamId(1L)).thenReturn(standaloneExecution());
    ExamLearnerService service = new ExamLearnerServiceImpl(
      candidateMapper, examMapper, mock(ExamAttemptMapper.class), mock(ExamPolicyMapper.class),
      mock(ExamAttemptQuestionMapper.class), mock(ExamAttemptOptionMapper.class),
      mock(ExamAnswerMapper.class), mock(ExamAnswerItemMapper.class), executionService,
      fileMapper, storageService);
    stpUtil = mockStatic(StpUtil.class);
    stpUtil.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
    when(examMapper.selectById(1L)).thenReturn(exam(1L));
    when(candidateMapper.selectOne(any())).thenReturn(candidate(20L, 1L, "NOT_STARTED"));
    com.sts.admin.entity.SysFileInfo file = new com.sts.admin.entity.SysFileInfo();
    file.setFileInfoId(900L);
    file.setFileName("exam-cover.png");
    file.setObjectName("DEMO/exam-covers/2026/08/id.png");
    file.setFileSize(4L);
    file.setFileSuffix("png");
    file.setVersion(1);
    when(fileMapper.selectById(900L)).thenReturn(file);
    org.springframework.core.io.ByteArrayResource resource = new org.springframework.core.io.ByteArrayResource("png!".getBytes());
    when(storageService.download("DEMO/exam-covers/2026/08/id.png")).thenReturn(resource);

    ExamLearnerService.ExamCoverFile cover = service.openCover(1L);

    assertThat(cover.fileName()).isEqualTo("exam-cover.png");
    assertThat(cover.contentType()).isEqualTo("image/png");
    assertThat(cover.fileSize()).isEqualTo(4L);
    assertThat(cover.etag()).isEqualTo("\"900-1-4\"");
    assertThat(cover.resource()).isSameAs(resource);
  }

  @Test
  void openCover_hidesUnassignedExamAs7001() {
    ExamMapper examMapper = mock(ExamMapper.class);
    AssessmentExecutionService executionService = mock(AssessmentExecutionService.class);
    when(executionService.findStandaloneByExamId(1L)).thenReturn(standaloneExecution());
    ExamLearnerService service = new ExamLearnerServiceImpl(
      mock(ExamCandidateMapper.class), examMapper, mock(ExamAttemptMapper.class), mock(ExamPolicyMapper.class),
      mock(ExamAttemptQuestionMapper.class), mock(ExamAttemptOptionMapper.class),
      mock(ExamAnswerMapper.class), mock(ExamAnswerItemMapper.class), executionService,
      mock(com.sts.admin.mapper.SysFileInfoMapper.class), mock(com.sts.admin.storage.StorageService.class));
    stpUtil = mockStatic(StpUtil.class);
    stpUtil.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
    when(examMapper.selectById(1L)).thenReturn(exam(1L));

    assertThatThrownBy(() -> service.openCover(1L))
      .isInstanceOfSatisfying(BusinessException.class, ex -> {
        assertThat(ex.getStatus()).isEqualTo(org.springframework.http.HttpStatus.NOT_FOUND);
        assertThat(ex.getCode()).isEqualTo(7001);
      });
  }

  @Test
  void openCover_hidesDraftExamAs7001() {
    ExamMapper examMapper = mock(ExamMapper.class);
    ExamLearnerService service = new ExamLearnerServiceImpl(
      mock(ExamCandidateMapper.class), examMapper, mock(ExamAttemptMapper.class), mock(ExamPolicyMapper.class),
      mock(ExamAttemptQuestionMapper.class), mock(ExamAttemptOptionMapper.class),
      mock(ExamAnswerMapper.class), mock(ExamAnswerItemMapper.class), mock(AssessmentExecutionService.class),
      mock(com.sts.admin.mapper.SysFileInfoMapper.class), mock(com.sts.admin.storage.StorageService.class));
    stpUtil = mockStatic(StpUtil.class);
    stpUtil.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
    Exam draft = exam(1L);
    draft.setPublishStatus("DRAFT");
    when(examMapper.selectById(1L)).thenReturn(draft);

    assertThatThrownBy(() -> service.openCover(1L))
      .isInstanceOfSatisfying(BusinessException.class, ex -> {
        assertThat(ex.getStatus()).isEqualTo(org.springframework.http.HttpStatus.NOT_FOUND);
        assertThat(ex.getCode()).isEqualTo(7001);
      });
  }

  @Test
  void openCover_missingFile_throws7071() {
    ExamCandidateMapper candidateMapper = mock(ExamCandidateMapper.class);
    ExamMapper examMapper = mock(ExamMapper.class);
    AssessmentExecutionService executionService = mock(AssessmentExecutionService.class);
    when(executionService.findStandaloneByExamId(1L)).thenReturn(standaloneExecution());
    ExamLearnerService service = new ExamLearnerServiceImpl(
      candidateMapper, examMapper, mock(ExamAttemptMapper.class), mock(ExamPolicyMapper.class),
      mock(ExamAttemptQuestionMapper.class), mock(ExamAttemptOptionMapper.class),
      mock(ExamAnswerMapper.class), mock(ExamAnswerItemMapper.class), executionService,
      mock(com.sts.admin.mapper.SysFileInfoMapper.class), mock(com.sts.admin.storage.StorageService.class));
    stpUtil = mockStatic(StpUtil.class);
    stpUtil.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
    Exam published = exam(1L);
    published.setCoverFileId(null);
    when(examMapper.selectById(1L)).thenReturn(published);
    when(candidateMapper.selectOne(any())).thenReturn(candidate(20L, 1L, "NOT_STARTED"));

    assertThatThrownBy(() -> service.openCover(1L))
      .isInstanceOfSatisfying(BusinessException.class, ex -> {
        assertThat(ex.getStatus()).isEqualTo(org.springframework.http.HttpStatus.NOT_FOUND);
        assertThat(ex.getCode()).isEqualTo(7071);
      });
  }

  @Test
  void getMyAttempt_restoresSavedAnswers_andHidesUnpublishedScores() {
    ExamCandidateMapper candidateMapper = mock(ExamCandidateMapper.class);
    ExamMapper examMapper = mock(ExamMapper.class);
    ExamAttemptMapper attemptMapper = mock(ExamAttemptMapper.class);
    ExamPolicyMapper policyMapper = mock(ExamPolicyMapper.class);
    ExamAttemptQuestionMapper questionMapper = mock(ExamAttemptQuestionMapper.class);
    ExamAttemptOptionMapper optionMapper = mock(ExamAttemptOptionMapper.class);
    ExamAnswerMapper answerMapper = mock(ExamAnswerMapper.class);
    ExamAnswerItemMapper itemMapper = mock(ExamAnswerItemMapper.class);
    ExamLearnerService service = service(candidateMapper, examMapper, attemptMapper, policyMapper,
                                         questionMapper, optionMapper, answerMapper, itemMapper);
    stpUtil = mockStatic(StpUtil.class);
    stpUtil.when(StpUtil::getLoginIdAsLong).thenReturn(7L);

    ExamAttempt attempt = attempt(40L, 20L, 7L, "PENDING_PUBLISH");
    attempt.setFinalScore(new BigDecimal("88"));
    attempt.setIsPassed(1);
    when(attemptMapper.selectById(40L)).thenReturn(attempt);
    when(candidateMapper.selectById(20L)).thenReturn(candidate(20L, 1L, "IN_PROGRESS"));
    when(examMapper.selectById(1L)).thenReturn(exam(1L));
    when(policyMapper.selectOne(any())).thenReturn(new ExamPolicy());
    ExamAttemptQuestion question = new ExamAttemptQuestion();
    question.setAttemptQuestionId(60L);
    question.setExamAttemptId(40L);
    question.setQuestionType("FILL");
    question.setCorrectAnswerSnapshotJson("{\"blanks\":[{\"blankNo\":1,\"answers\":[\"安全\"]},{\"blankNo\":2,\"answers\":[\"生产\"]}]}");
    when(questionMapper.selectList(any())).thenReturn(List.of(question));
    when(optionMapper.selectList(any())).thenReturn(List.of());
    ExamAnswer answer = new ExamAnswer();
    answer.setExamAnswerId(70L);
    answer.setExamAttemptId(40L);
    answer.setAttemptQuestionId(60L);
    answer.setAnswerText("A,B");
    answer.setIsAnswered(1);
    answer.setFinalScore(new BigDecimal("10"));
    answer.setIsCorrect(1);
    when(answerMapper.selectList(any())).thenReturn(List.of(answer));
    ExamAnswerItem item = new ExamAnswerItem();
    item.setExamAnswerId(70L);
    item.setItemType("OPTION");
    item.setItemKey("A");
    when(itemMapper.selectList(any())).thenReturn(List.of(item));

    ExamAttemptVO result = service.getMyAttempt(40L);

    assertThat(result.getScorePublishStatus()).isEqualTo("UNPUBLISHED");
    assertThat(result.getFinalScore()).isNull();
    assertThat(result.getQuestions().getFirst().getAnswer().getAnswerText()).isEqualTo("A,B");
    assertThat(result.getQuestions().getFirst().getAnswer().getFinalScore()).isNull();
    assertThat(result.getQuestions().getFirst().getAnswer().getItems()).hasSize(1);
    assertThat(result.getQuestions().getFirst().getBlankCount()).isEqualTo(2);
    assertThat(result.getQuestions().getFirst().getCorrectAnswerSnapshotJson()).isNull();
  }

  @Test
  void getMyAttempt_rejectsOtherUsersAttempt() {
    ExamCandidateMapper candidateMapper = mock(ExamCandidateMapper.class);
    ExamAttemptMapper attemptMapper = mock(ExamAttemptMapper.class);
    ExamLearnerService service = service(candidateMapper,
                                         mock(ExamMapper.class),
                                         attemptMapper,
                                         mock(ExamPolicyMapper.class),
                                         mock(ExamAttemptQuestionMapper.class),
                                         mock(ExamAttemptOptionMapper.class),
                                         mock(ExamAnswerMapper.class),
                                         mock(ExamAnswerItemMapper.class));
    stpUtil = mockStatic(StpUtil.class);
    stpUtil.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
    when(attemptMapper.selectById(40L)).thenReturn(attempt(40L, 20L, 8L, "ANSWERING"));
    when(candidateMapper.selectById(20L)).thenReturn(candidate(20L, 1L, "IN_PROGRESS"));

    assertThatThrownBy(() -> service.getMyAttempt(40L))
      .isInstanceOf(BusinessException.class)
      .hasMessageContaining("无权访问");
  }

  @Test
  void getMyAttempt_publishedDetailModeShowsCorrectAnswer_andAnalysisPolicyControlsAnalysis() {
    ExamCandidateMapper candidateMapper = mock(ExamCandidateMapper.class);
    ExamMapper examMapper = mock(ExamMapper.class);
    ExamAttemptMapper attemptMapper = mock(ExamAttemptMapper.class);
    ExamPolicyMapper policyMapper = mock(ExamPolicyMapper.class);
    ExamAttemptQuestionMapper questionMapper = mock(ExamAttemptQuestionMapper.class);
    ExamAttemptOptionMapper optionMapper = mock(ExamAttemptOptionMapper.class);
    AssessmentExecution execution = standaloneExecution();
    execution.setPolicySnapshotJson("{\"isAnticheatEnabled\":1,\"isWatermarkEnabled\":1}");
    ExamLearnerService service = service(candidateMapper, examMapper, attemptMapper, policyMapper,
                                         questionMapper, optionMapper, mock(ExamAnswerMapper.class),
                                         mock(ExamAnswerItemMapper.class), execution);
    stpUtil = mockStatic(StpUtil.class);
    stpUtil.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
    ExamAttempt attempt = attempt(40L, 20L, 7L, "PUBLISHED");
    attempt.setSubmittedTime(LocalDateTime.now().minusMinutes(5));
    attempt.setScorePublishedTime(LocalDateTime.now());
    when(attemptMapper.selectById(40L)).thenReturn(attempt);
    when(candidateMapper.selectById(20L)).thenReturn(candidate(20L, 1L, "PASSED"));
    when(examMapper.selectById(1L)).thenReturn(exam(1L));
    ExamPolicy policy = new ExamPolicy();
    policy.setAnswerDetailMode("SUBMITTED");
    policy.setIsScoreVisible(1);
    policy.setIsAnalysisVisible(0);
    when(policyMapper.selectOne(any())).thenReturn(policy);
    ExamAttemptQuestion question = new ExamAttemptQuestion();
    question.setAttemptQuestionId(60L);
    question.setExamAttemptId(40L);
    question.setAnalysisSnapshot("解析内容");
    question.setCorrectAnswerSnapshotJson("{\"standardAnswer\":\"A\",\"correctOptions\":[\"A\"]}");
    when(questionMapper.selectList(any())).thenReturn(List.of(question));
    ExamAttemptOption option = new ExamAttemptOption();
    option.setAttemptQuestionId(60L);
    option.setOptionKey("A");
    option.setIsCorrectSnapshot(1);
    when(optionMapper.selectList(any())).thenReturn(List.of(option));

    ExamAttemptVO result = service.getMyAttempt(40L);

    assertThat(result.getIsAnticheatEnabled()).isEqualTo(1);
    assertThat(result.getIsWatermarkEnabled()).isEqualTo(1);
    assertThat(result.getQuestions().getFirst().getStandardAnswer()).isEqualTo("A");
    assertThat(result.getQuestions().getFirst().getCorrectAnswerSnapshotJson()).contains("correctOptions");
    assertThat(result.getQuestions().getFirst().getOptions().getFirst().getIsCorrectSnapshot()).isEqualTo(1);
    assertThat(result.getQuestions().getFirst().getAnalysisSnapshot()).isNull();
  }

  @Test
  void getMyAttempt_trainingExamDeliversFrozenAnticheatAndWatermarkFromSnapshot() {
    ExamCandidateMapper candidateMapper = mock(ExamCandidateMapper.class);
    ExamMapper examMapper = mock(ExamMapper.class);
    ExamAttemptMapper attemptMapper = mock(ExamAttemptMapper.class);
    ExamPolicyMapper policyMapper = mock(ExamPolicyMapper.class);
    ExamAttemptQuestionMapper questionMapper = mock(ExamAttemptQuestionMapper.class);
    AssessmentExecution execution = trainingExecution("TRAINING_EXAM");
    execution.setPolicySnapshotJson("{\"isAnticheatEnabled\":1,\"isWatermarkEnabled\":1}");
    ExamLearnerService service = service(candidateMapper, examMapper, attemptMapper, policyMapper,
                                         questionMapper, mock(ExamAttemptOptionMapper.class),
                                         mock(ExamAnswerMapper.class), mock(ExamAnswerItemMapper.class), execution);
    stpUtil = mockStatic(StpUtil.class);
    stpUtil.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
    ExamAttempt attempt = attempt(40L, 20L, 7L, "ANSWERING");
    attempt.setSourceType("TRAINING_EXAM");
    when(attemptMapper.selectById(40L)).thenReturn(attempt);
    when(candidateMapper.selectById(20L)).thenReturn(candidate(20L, 1L, "IN_PROGRESS"));
    when(examMapper.selectById(1L)).thenReturn(exam(1L));
    // 实时策略表与冻结快照不一致时，培训专属考试也必须以发布时的快照为准。
    ExamPolicy policy = new ExamPolicy();
    policy.setIsAnticheatEnabled(0);
    policy.setIsWatermarkEnabled(0);
    when(policyMapper.selectOne(any())).thenReturn(policy);
    when(questionMapper.selectList(any())).thenReturn(List.of());

    ExamAttemptVO result = service.getMyAttempt(40L);

    assertThat(result.getIsAnticheatEnabled()).isEqualTo(1);
    assertThat(result.getIsWatermarkEnabled()).isEqualTo(1);
  }

  @Test
  void getMyAttempt_courseQuizEffective_showsScoreStandardAnswerAndAnalysisWithoutExamPolicy() {
    ExamCandidateMapper candidateMapper = mock(ExamCandidateMapper.class);
    ExamAttemptMapper attemptMapper = mock(ExamAttemptMapper.class);
    ExamAttemptQuestionMapper questionMapper = mock(ExamAttemptQuestionMapper.class);
    ExamAttemptOptionMapper optionMapper = mock(ExamAttemptOptionMapper.class);
    ExamAnswerMapper answerMapper = mock(ExamAnswerMapper.class);
    ExamAnswerItemMapper itemMapper = mock(ExamAnswerItemMapper.class);
    ExamLearnerService service = service(candidateMapper, mock(ExamMapper.class), attemptMapper,
                                         mock(ExamPolicyMapper.class), questionMapper, optionMapper,
                                         answerMapper, itemMapper, courseExecution("COURSE_QUIZ"));
    stpUtil = mockStatic(StpUtil.class);
    stpUtil.when(StpUtil::getLoginIdAsLong).thenReturn(7L);

    ExamAttempt attempt = courseAttempt("COURSE_QUIZ", "QUIZ", "RESULT_EFFECTIVE");
    attempt.setResultEffectiveTime(LocalDateTime.now());
    attempt.setFinalScore(new BigDecimal("88"));
    attempt.setIsPassed(1);
    attempt.setCorrectCount(1);
    attempt.setWrongCount(0);
    when(attemptMapper.selectById(40L)).thenReturn(attempt);
    when(candidateMapper.selectById(20L)).thenReturn(candidate(20L, null, "PASSED"));
    stubEffectiveQuestion(questionMapper, optionMapper, answerMapper, itemMapper);

    ExamAttemptVO result = service.getMyAttempt(40L);

    assertThat(result.getScorePublishStatus()).isEqualTo("EFFECTIVE");
    assertThat(result.getScoreVisible()).isTrue();
    assertThat(result.getFinalScore()).isEqualByComparingTo("88");
    assertThat(result.getIsPassed()).isEqualTo(1);
    assertThat(result.getQuestions().getFirst().getStandardAnswer()).isEqualTo("A");
    assertThat(result.getQuestions().getFirst().getAnalysisSnapshot()).isEqualTo("解析内容");
    assertThat(result.getQuestions().getFirst().getOptions().getFirst().getIsCorrectSnapshot()).isEqualTo(1);
    assertThat(result.getQuestions().getFirst().getAnswer().getFinalScore()).isEqualByComparingTo("10");
    assertThat(result.getQuestions().getFirst().getAnswer().getIsCorrect()).isEqualTo(1);
    assertThat(result.getQuestions().getFirst().getAnswer().getReviewComment()).isNull();
  }

  @Test
  void getMyAttempt_courseBankPracticeEffective_showsScoreAndAnswers() {
    ExamCandidateMapper candidateMapper = mock(ExamCandidateMapper.class);
    ExamAttemptMapper attemptMapper = mock(ExamAttemptMapper.class);
    ExamAttemptQuestionMapper questionMapper = mock(ExamAttemptQuestionMapper.class);
    ExamAttemptOptionMapper optionMapper = mock(ExamAttemptOptionMapper.class);
    ExamAnswerMapper answerMapper = mock(ExamAnswerMapper.class);
    ExamAnswerItemMapper itemMapper = mock(ExamAnswerItemMapper.class);
    ExamLearnerService service = service(candidateMapper, mock(ExamMapper.class), attemptMapper,
                                         mock(ExamPolicyMapper.class), questionMapper, optionMapper,
                                         answerMapper, itemMapper, courseExecution("COURSE_BANK_PRACTICE"));
    stpUtil = mockStatic(StpUtil.class);
    stpUtil.when(StpUtil::getLoginIdAsLong).thenReturn(7L);

    ExamAttempt attempt = courseAttempt("COURSE_BANK_PRACTICE", "BANK_PRACTICE", "RESULT_EFFECTIVE");
    attempt.setResultEffectiveTime(LocalDateTime.now());
    attempt.setFinalScore(new BigDecimal("70"));
    attempt.setIsPassed(1);
    when(attemptMapper.selectById(40L)).thenReturn(attempt);
    when(candidateMapper.selectById(20L)).thenReturn(candidate(20L, null, "PASSED"));
    stubEffectiveQuestion(questionMapper, optionMapper, answerMapper, itemMapper);

    ExamAttemptVO result = service.getMyAttempt(40L);

    assertThat(result.getScorePublishStatus()).isEqualTo("EFFECTIVE");
    assertThat(result.getScoreVisible()).isTrue();
    assertThat(result.getFinalScore()).isEqualByComparingTo("70");
    assertThat(result.getQuestions().getFirst().getStandardAnswer()).isEqualTo("A");
    assertThat(result.getQuestions().getFirst().getAnalysisSnapshot()).isEqualTo("解析内容");
  }

  @Test
  void getMyAttempt_trainingPracticeEffective_showsFeedbackWithoutScore() {
    ExamCandidateMapper candidateMapper = mock(ExamCandidateMapper.class);
    ExamAttemptMapper attemptMapper = mock(ExamAttemptMapper.class);
    ExamAttemptQuestionMapper questionMapper = mock(ExamAttemptQuestionMapper.class);
    ExamAttemptOptionMapper optionMapper = mock(ExamAttemptOptionMapper.class);
    ExamAnswerMapper answerMapper = mock(ExamAnswerMapper.class);
    ExamAnswerItemMapper itemMapper = mock(ExamAnswerItemMapper.class);
    ExamLearnerService service = service(candidateMapper, mock(ExamMapper.class), attemptMapper,
                                         mock(ExamPolicyMapper.class), questionMapper, optionMapper,
                                         answerMapper, itemMapper, courseExecution("TRAINING_PRACTICE"));
    stpUtil = mockStatic(StpUtil.class);
    stpUtil.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
    ExamAttempt attempt = courseAttempt("TRAINING_PRACTICE", "BANK_PRACTICE", "RESULT_EFFECTIVE");
    attempt.setResultEffectiveTime(LocalDateTime.now());
    attempt.setTotalScoreSnapshot(null);
    attempt.setFinalScore(null);
    attempt.setIsPassed(null);
    attempt.setEvaluatedCount(1);
    attempt.setCorrectCount(1);
    attempt.setWrongCount(0);
    attempt.setUnassessedCount(1);
    attempt.setAccuracyRate(new BigDecimal("100.00"));
    when(attemptMapper.selectById(40L)).thenReturn(attempt);
    when(candidateMapper.selectById(20L)).thenReturn(candidate(20L, null, "COMPLETED"));
    stubEffectiveQuestion(questionMapper, optionMapper, answerMapper, itemMapper);

    ExamAttemptVO result = service.getMyAttempt(40L);

    assertThat(result.getScorePublishStatus()).isEqualTo("EFFECTIVE");
    assertThat(result.getScoreVisible()).isFalse();
    assertThat(result.getFinalScore()).isNull();
    assertThat(result.getIsPassed()).isNull();
    assertThat(result.getEvaluatedCount()).isEqualTo(1);
    assertThat(result.getAccuracyRate()).isEqualByComparingTo("100.00");
    assertThat(result.getQuestions().getFirst().getStandardAnswer()).isEqualTo("A");
    assertThat(result.getQuestions().getFirst().getAnswer().getIsCorrect()).isEqualTo(1);
  }

  @Test
  void getMyAttempt_courseQuizPendingReview_hidesScoreAndStandardAnswer() {
    ExamCandidateMapper candidateMapper = mock(ExamCandidateMapper.class);
    ExamAttemptMapper attemptMapper = mock(ExamAttemptMapper.class);
    ExamAttemptQuestionMapper questionMapper = mock(ExamAttemptQuestionMapper.class);
    ExamAttemptOptionMapper optionMapper = mock(ExamAttemptOptionMapper.class);
    ExamAnswerMapper answerMapper = mock(ExamAnswerMapper.class);
    ExamAnswerItemMapper itemMapper = mock(ExamAnswerItemMapper.class);
    ExamLearnerService service = service(candidateMapper, mock(ExamMapper.class), attemptMapper,
                                         mock(ExamPolicyMapper.class), questionMapper, optionMapper,
                                         answerMapper, itemMapper, courseExecution("COURSE_QUIZ"));
    stpUtil = mockStatic(StpUtil.class);
    stpUtil.when(StpUtil::getLoginIdAsLong).thenReturn(7L);

    ExamAttempt attempt = courseAttempt("COURSE_QUIZ", "QUIZ", "PENDING_REVIEW");
    attempt.setFinalScore(new BigDecimal("40"));
    attempt.setIsPassed(0);
    when(attemptMapper.selectById(40L)).thenReturn(attempt);
    when(candidateMapper.selectById(20L)).thenReturn(candidate(20L, null, "PENDING_REVIEW"));
    stubEffectiveQuestion(questionMapper, optionMapper, answerMapper, itemMapper);

    ExamAttemptVO result = service.getMyAttempt(40L);

    assertThat(result.getScorePublishStatus()).isEqualTo("UNPUBLISHED");
    assertThat(result.getScoreVisible()).isFalse();
    assertThat(result.getFinalScore()).isNull();
    assertThat(result.getQuestions().getFirst().getStandardAnswer()).isNull();
    assertThat(result.getQuestions().getFirst().getAnalysisSnapshot()).isNull();
    assertThat(result.getQuestions().getFirst().getCorrectAnswerSnapshotJson()).isNull();
    assertThat(result.getQuestions().getFirst().getOptions().getFirst().getIsCorrectSnapshot()).isNull();
    assertThat(result.getQuestions().getFirst().getAnswer().getAnswerText()).isEqualTo("A");
    assertThat(result.getQuestions().getFirst().getAnswer().getFinalScore()).isNull();
  }

  @DisplayName("pageMyTasks：线下考试未录分时不给出任何操作，状态为等待结果")
  @Test
  void pageMyTasks_offlineExamWithoutRecordedScore_hasWaitingStatusAndNoAction() {
    ExamCandidateMapper candidateMapper = mock(ExamCandidateMapper.class);
    ExamMapper examMapper = mock(ExamMapper.class);
    ExamAttemptMapper attemptMapper = mock(ExamAttemptMapper.class);
    ExamPolicyMapper policyMapper = mock(ExamPolicyMapper.class);
    AssessmentExecution execution = standaloneExecution();
    execution.setResourceType("OFFLINE_RESULT");
    ExamLearnerService service = service(candidateMapper, examMapper, attemptMapper, policyMapper,
                                         mock(ExamAttemptQuestionMapper.class), mock(ExamAttemptOptionMapper.class),
                                         mock(ExamAnswerMapper.class), mock(ExamAnswerItemMapper.class), execution);
    stpUtil = mockStatic(StpUtil.class);
    stpUtil.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
    when(candidateMapper.selectList(any())).thenReturn(List.of(candidate(20L, 1L, "PENDING")));
    Exam offline = exam(1L);
    offline.setExamType("OFFLINE");
    when(examMapper.selectBatchIds(List.of(1L))).thenReturn(List.of(offline));
    when(attemptMapper.selectList(any())).thenReturn(List.of());
    when(policyMapper.selectList(any())).thenReturn(List.of());

    var page = service.pageMyTasks(new PageRequest<>(1, 20, new ExamLearnerTaskQuery()));

    ExamLearnerTaskVO task = page.list().getFirst();
    assertThat(task.getTaskStatus()).isEqualTo("WAITING_RESULT");
    assertThat(task.getAvailableAction()).isNull();
  }

  @DisplayName("pageMyTasks：线下考试录分后学员可查看结果")
  @Test
  void pageMyTasks_offlineExamWithRecordedScore_returnsViewResult() {
    ExamCandidateMapper candidateMapper = mock(ExamCandidateMapper.class);
    ExamMapper examMapper = mock(ExamMapper.class);
    ExamAttemptMapper attemptMapper = mock(ExamAttemptMapper.class);
    ExamPolicyMapper policyMapper = mock(ExamPolicyMapper.class);
    AssessmentExecution execution = standaloneExecution();
    execution.setResourceType("OFFLINE_RESULT");
    ExamLearnerService service = service(candidateMapper, examMapper, attemptMapper, policyMapper,
                                         mock(ExamAttemptQuestionMapper.class), mock(ExamAttemptOptionMapper.class),
                                         mock(ExamAnswerMapper.class), mock(ExamAnswerItemMapper.class), execution);
    stpUtil = mockStatic(StpUtil.class);
    stpUtil.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
    when(candidateMapper.selectList(any())).thenReturn(List.of(candidate(20L, 1L, "PASSED")));
    Exam offline = exam(1L);
    offline.setExamType("OFFLINE");
    when(examMapper.selectBatchIds(List.of(1L))).thenReturn(List.of(offline));
    ExamAttempt attempt = attempt(40L, 20L, 7L, "PUBLISHED");
    attempt.setScorePublishedTime(LocalDateTime.now());
    attempt.setSubmitReason("OFFLINE_IMPORT");
    attempt.setFinalScore(new BigDecimal("88"));
    attempt.setIsPassed(1);
    when(attemptMapper.selectList(any())).thenReturn(List.of(attempt));
    ExamPolicy policy = new ExamPolicy();
    policy.setExamId(1L);
    policy.setIsScoreVisible(1);
    when(policyMapper.selectList(any())).thenReturn(List.of(policy));

    var page = service.pageMyTasks(new PageRequest<>(1, 20, new ExamLearnerTaskQuery()));

    ExamLearnerTaskVO task = page.list().getFirst();
    assertThat(task.getTaskStatus()).isEqualTo("RESULT_PUBLISHED");
    assertThat(task.getAvailableAction()).isEqualTo("VIEW_RESULT");
    assertThat(task.getFinalScore()).isEqualByComparingTo("88");
  }

  @DisplayName("pageMyTasks：独立题库练习自动生效后可直接查看结果与解析")
  @Test
  void pageMyTasks_standaloneBankPracticeEffective_returnsViewResult() {
    ExamCandidateMapper candidateMapper = mock(ExamCandidateMapper.class);
    ExamMapper examMapper = mock(ExamMapper.class);
    ExamAttemptMapper attemptMapper = mock(ExamAttemptMapper.class);
    ExamPolicyMapper policyMapper = mock(ExamPolicyMapper.class);
    AssessmentExecution execution = standaloneExecution();
    execution.setSourceType("STANDALONE_PRACTICE");
    execution.setAssessmentKind("BANK_PRACTICE");
    ExamLearnerService service = service(candidateMapper, examMapper, attemptMapper, policyMapper,
                                         mock(ExamAttemptQuestionMapper.class), mock(ExamAttemptOptionMapper.class),
                                         mock(ExamAnswerMapper.class), mock(ExamAnswerItemMapper.class), execution);
    stpUtil = mockStatic(StpUtil.class);
    stpUtil.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
    when(candidateMapper.selectList(any())).thenReturn(List.of(candidate(20L, 1L, "COMPLETED")));
    Exam practice = exam(1L);
    practice.setExamType("BANK_PRACTICE");
    when(examMapper.selectBatchIds(List.of(1L))).thenReturn(List.of(practice));
    ExamAttempt attempt = attempt(40L, 20L, 7L, "RESULT_EFFECTIVE");
    attempt.setSourceType("STANDALONE_PRACTICE");
    attempt.setAssessmentKind("BANK_PRACTICE");
    attempt.setResultEffectiveTime(LocalDateTime.now());
    attempt.setFinalScore(new BigDecimal("4"));
    when(attemptMapper.selectList(any())).thenReturn(List.of(attempt));
    when(policyMapper.selectList(any())).thenReturn(List.of());

    var page = service.pageMyTasks(new PageRequest<>(1, 20, new ExamLearnerTaskQuery()));

    ExamLearnerTaskVO task = page.list().getFirst();
    assertThat(task.getScorePublishStatus()).isEqualTo("EFFECTIVE");
    assertThat(task.getTaskStatus()).isEqualTo("RESULT_PUBLISHED");
    assertThat(task.getAvailableAction()).isEqualTo("VIEW_RESULT");
    assertThat(task.getScoreVisible()).isTrue();
    assertThat(task.getFinalScore()).isEqualByComparingTo("4");
    assertThat(task.getIsPassed()).isNull();
  }

  @DisplayName("getMyAttempt：独立题库练习自动生效后返回得分与答案解析")
  @Test
  void getMyAttempt_standaloneBankPracticeEffective_showsFeedback() {
    ExamCandidateMapper candidateMapper = mock(ExamCandidateMapper.class);
    ExamMapper examMapper = mock(ExamMapper.class);
    ExamAttemptMapper attemptMapper = mock(ExamAttemptMapper.class);
    ExamPolicyMapper policyMapper = mock(ExamPolicyMapper.class);
    ExamAttemptQuestionMapper questionMapper = mock(ExamAttemptQuestionMapper.class);
    ExamAttemptOptionMapper optionMapper = mock(ExamAttemptOptionMapper.class);
    ExamAnswerMapper answerMapper = mock(ExamAnswerMapper.class);
    ExamAnswerItemMapper itemMapper = mock(ExamAnswerItemMapper.class);
    AssessmentExecution execution = standaloneExecution();
    execution.setSourceType("STANDALONE_PRACTICE");
    execution.setAssessmentKind("BANK_PRACTICE");
    ExamLearnerService service = service(candidateMapper, examMapper, attemptMapper, policyMapper,
                                         questionMapper, optionMapper, answerMapper, itemMapper, execution);
    stpUtil = mockStatic(StpUtil.class);
    stpUtil.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
    ExamAttempt attempt = attempt(40L, 20L, 7L, "RESULT_EFFECTIVE");
    attempt.setSourceType("STANDALONE_PRACTICE");
    attempt.setAssessmentKind("BANK_PRACTICE");
    attempt.setResultEffectiveTime(LocalDateTime.now());
    attempt.setFinalScore(new BigDecimal("4"));
    when(attemptMapper.selectById(40L)).thenReturn(attempt);
    when(candidateMapper.selectById(20L)).thenReturn(candidate(20L, 1L, "COMPLETED"));
    Exam practice = exam(1L);
    practice.setExamType("BANK_PRACTICE");
    when(examMapper.selectById(1L)).thenReturn(practice);
    ExamPolicy policy = new ExamPolicy();
    // 题库练习交卷即反馈，不受正式考试的解析开关限制。
    policy.setIsAnalysisVisible(0);
    when(policyMapper.selectOne(any())).thenReturn(policy);
    stubEffectiveQuestion(questionMapper, optionMapper, answerMapper, itemMapper);

    ExamAttemptVO result = service.getMyAttempt(40L);

    assertThat(result.getScorePublishStatus()).isEqualTo("EFFECTIVE");
    assertThat(result.getScoreVisible()).isTrue();
    assertThat(result.getFinalScore()).isEqualByComparingTo("4");
    assertThat(result.getIsPassed()).isNull();
    assertThat(result.getQuestions().getFirst().getStandardAnswer()).isEqualTo("A");
    assertThat(result.getQuestions().getFirst().getAnalysisSnapshot()).isEqualTo("解析内容");
  }

  @DisplayName("getMyAttempt：answerDetailMode=PUBLISHED 在成绩发布后可见答案")
  @Test
  void getMyAttempt_publishedMode_showsAnswerAfterScorePublished() {
    assertThat(detailedAnswerForMode("PUBLISHED", "PUBLISHED")).isEqualTo("A");
  }

  @DisplayName("getMyAttempt：answerDetailMode=SUBMITTED 在交卷后不等待成绩发布")
  @Test
  void getMyAttempt_submittedMode_showsAnswerBeforeScorePublished() {
    assertThat(detailedAnswerForMode("SUBMITTED", "PENDING_PUBLISH")).isEqualTo("A");
  }

  @DisplayName("getMyAttempt：answerDetailMode=NEVER 始终不可见答案")
  @Test
  void getMyAttempt_neverMode_hidesAnswer() {
    assertThat(detailedAnswerForMode("NEVER", "PUBLISHED")).isNull();
  }

  /** 以指定 answerDetailMode 读取已发布作答的首题标准答案，用于覆盖可见性分支。 */
  private String detailedAnswerForMode(String answerDetailMode, String attemptStatus) {
    ExamCandidateMapper candidateMapper = mock(ExamCandidateMapper.class);
    ExamMapper examMapper = mock(ExamMapper.class);
    ExamAttemptMapper attemptMapper = mock(ExamAttemptMapper.class);
    ExamPolicyMapper policyMapper = mock(ExamPolicyMapper.class);
    ExamAttemptQuestionMapper questionMapper = mock(ExamAttemptQuestionMapper.class);
    ExamLearnerService service = service(candidateMapper, examMapper, attemptMapper, policyMapper,
                                         questionMapper, mock(ExamAttemptOptionMapper.class),
                                         mock(ExamAnswerMapper.class), mock(ExamAnswerItemMapper.class));
    stpUtil = mockStatic(StpUtil.class);
    stpUtil.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
    ExamAttempt attempt = attempt(40L, 20L, 7L, attemptStatus);
    attempt.setSubmittedTime(LocalDateTime.now());
    if ("PUBLISHED".equals(attemptStatus)) attempt.setScorePublishedTime(LocalDateTime.now());
    when(attemptMapper.selectById(40L)).thenReturn(attempt);
    when(candidateMapper.selectById(20L)).thenReturn(candidate(20L, 1L, "PASSED"));
    when(examMapper.selectById(1L)).thenReturn(exam(1L));
    ExamPolicy policy = new ExamPolicy();
    policy.setAnswerDetailMode(answerDetailMode);
    policy.setIsScoreVisible(1);
    when(policyMapper.selectOne(any())).thenReturn(policy);
    ExamAttemptQuestion question = new ExamAttemptQuestion();
    question.setAttemptQuestionId(60L);
    question.setExamAttemptId(40L);
    question.setCorrectAnswerSnapshotJson("{\"standardAnswer\":\"A\",\"correctOptions\":[\"A\"]}");
    when(questionMapper.selectList(any())).thenReturn(List.of(question));
    return service.getMyAttempt(40L).getQuestions().getFirst().getStandardAnswer();
  }

  private ExamLearnerService service(ExamCandidateMapper candidateMapper, ExamMapper examMapper,
                                     ExamAttemptMapper attemptMapper, ExamPolicyMapper policyMapper,
                                     ExamAttemptQuestionMapper questionMapper, ExamAttemptOptionMapper optionMapper,
                                     ExamAnswerMapper answerMapper, ExamAnswerItemMapper itemMapper) {
    return service(candidateMapper, examMapper, attemptMapper, policyMapper, questionMapper, optionMapper,
                   answerMapper, itemMapper, standaloneExecution());
  }

  private ExamLearnerService service(ExamCandidateMapper candidateMapper, ExamMapper examMapper,
                                     ExamAttemptMapper attemptMapper, ExamPolicyMapper policyMapper,
                                     ExamAttemptQuestionMapper questionMapper, ExamAttemptOptionMapper optionMapper,
                                     ExamAnswerMapper answerMapper, ExamAnswerItemMapper itemMapper,
                                     AssessmentExecution execution) {
    AssessmentExecutionService executionService = mock(AssessmentExecutionService.class);
    when(executionService.requireById(any())).thenReturn(execution);
    return new ExamLearnerServiceImpl(candidateMapper, examMapper, attemptMapper, policyMapper,
                                      questionMapper, optionMapper, answerMapper, itemMapper, executionService,
                                      mock(com.sts.admin.mapper.SysFileInfoMapper.class),
                                      mock(com.sts.admin.storage.StorageService.class));
  }

  private void stubEffectiveQuestion(ExamAttemptQuestionMapper questionMapper,
                                     ExamAttemptOptionMapper optionMapper,
                                     ExamAnswerMapper answerMapper,
                                     ExamAnswerItemMapper itemMapper) {
    ExamAttemptQuestion question = new ExamAttemptQuestion();
    question.setAttemptQuestionId(60L);
    question.setExamAttemptId(40L);
    question.setQuestionType("SINGLE");
    question.setAnalysisSnapshot("解析内容");
    question.setCorrectAnswerSnapshotJson("{\"standardAnswer\":\"A\",\"correctOptions\":[\"A\"]}");
    when(questionMapper.selectList(any())).thenReturn(List.of(question));
    ExamAttemptOption option = new ExamAttemptOption();
    option.setAttemptQuestionId(60L);
    option.setOptionKey("A");
    option.setIsCorrectSnapshot(1);
    when(optionMapper.selectList(any())).thenReturn(List.of(option));
    ExamAnswer answer = new ExamAnswer();
    answer.setExamAnswerId(70L);
    answer.setExamAttemptId(40L);
    answer.setAttemptQuestionId(60L);
    answer.setAnswerText("A");
    answer.setIsAnswered(1);
    answer.setFinalScore(new BigDecimal("10"));
    answer.setIsCorrect(1);
    when(answerMapper.selectList(any())).thenReturn(List.of(answer));
    ExamAnswerItem item = new ExamAnswerItem();
    item.setExamAnswerId(70L);
    item.setItemType("OPTION");
    item.setItemKey("A");
    when(itemMapper.selectList(any())).thenReturn(List.of(item));
  }

  private AssessmentExecution standaloneExecution() {
    AssessmentExecution execution = new AssessmentExecution();
    execution.setAssessmentExecutionId(11L);
    execution.setExamId(1L);
    execution.setSourceType("STANDALONE_EXAM");
    execution.setSourceBusinessId(1L);
    return execution;
  }

  private AssessmentExecution trainingExecution(String sourceType) {
    AssessmentExecution execution = new AssessmentExecution();
    execution.setAssessmentExecutionId(11L);
    execution.setExamId(1L);
    execution.setSourceType(sourceType);
    execution.setSourceBusinessId(1L);
    return execution;
  }

  private AssessmentExecution courseExecution(String sourceType) {
    AssessmentExecution execution = new AssessmentExecution();
    execution.setAssessmentExecutionId(11L);
    execution.setExamId(null);
    execution.setSourceType(sourceType);
    execution.setSourceBusinessId(101L);
    return execution;
  }

  private ExamAttempt courseAttempt(String sourceType, String assessmentKind, String status) {
    ExamAttempt value = attempt(40L, 20L, 7L, status);
    value.setSourceType(sourceType);
    value.setAssessmentKind(assessmentKind);
    return value;
  }

  private ExamCandidate candidate(Long id, Long examId, String status) {
    ExamCandidate value = new ExamCandidate();
    value.setExamCandidateId(id);
    value.setAssessmentExecutionId(11L);
    value.setUserId(7L);
    value.setCandidateStatus(status);
    value.setAttemptCount(1);
    return value;
  }

  private Exam exam(Long id) {
    Exam value = new Exam();
    value.setExamId(id);
    value.setExamName("安全生产考试");
    value.setExamType("ONLINE");
    value.setCoverFileId(900L);
    value.setOpenEndTime(LocalDateTime.now().plusDays(1));
    value.setDurationSeconds(3600);
    value.setPublishStatus("PUBLISHED");
    return value;
  }

  private ExamAttempt attempt(Long id, Long candidateId, Long userId, String status) {
    ExamAttempt value = new ExamAttempt();
    value.setExamAttemptId(id);
    value.setAssessmentExecutionId(11L);
    value.setSourceType("STANDALONE_EXAM");
    value.setAssessmentKind("FORMAL_EXAM");
    value.setExamCandidateId(candidateId);
    value.setUserId(userId);
    value.setAttemptStatus(status);
    return value;
  }
}

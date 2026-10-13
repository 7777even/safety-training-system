package com.sts.exam.service;

import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sts.certificate.service.CertificateAutoIssueService;
import com.sts.common.exception.BusinessException;
import com.sts.course.dto.CourseAssessmentResultEffective;
import com.sts.course.dto.CourseAssessmentSubmitted;
import com.sts.course.service.CourseLearningService;
import com.sts.exam.dto.ReviewAnswerRequest;
import com.sts.exam.dto.StartAttemptRequest;
import com.sts.exam.dto.SubmitAttemptRequest;
import com.sts.exam.entity.*;
import com.sts.exam.mapper.*;
import com.sts.paper.entity.PaperVersion;
import com.sts.paper.entity.PaperVersionQuestion;
import com.sts.paper.mapper.PaperVersionMapper;
import com.sts.paper.mapper.PaperVersionQuestionMapper;
import com.sts.question.entity.QuestionVersion;
import com.sts.question.mapper.QuestionBlankAnswerMapper;
import com.sts.question.mapper.QuestionOptionMapper;
import com.sts.question.mapper.QuestionVersionMapper;
import com.sts.question.service.QuestionCandidatePoolService;
import com.sts.training.event.TrainingParticipantContentFactChangedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ExamAttemptServiceImplTest {

  private ExamMapper examMapper;
  private ExamCandidateMapper examCandidateMapper;
  private ExamPolicyMapper examPolicyMapper;
  private ExamAttemptMapper examAttemptMapper;
  private ExamAttemptQuestionMapper attemptQuestionMapper;
  private ExamAttemptOptionMapper attemptOptionMapper;
  private ExamAnswerMapper examAnswerMapper;
  private ExamAnswerItemMapper examAnswerItemMapper;
  private ExamReviewRecordMapper reviewRecordMapper;
  private ExamReviewerMapper examReviewerMapper;
  private ExamScorePublishLogMapper scorePublishLogMapper;
  private ExamAnticheatEventMapper anticheatEventMapper;
  private ExamPracticeResetMapper practiceResetMapper;
  private PaperVersionMapper paperVersionMapper;
  private PaperVersionQuestionMapper paperVersionQuestionMapper;
  private QuestionVersionMapper questionVersionMapper;
  private QuestionOptionMapper questionOptionMapper;
  private QuestionBlankAnswerMapper blankAnswerMapper;
  private AssessmentExecutionService assessmentExecutionService;
  private QuestionCandidatePoolService candidatePoolService;
  private AssessmentExecutionQuestionPoolService executionQuestionPoolService;
  private CourseLearningService courseLearningService;
  private ApplicationEventPublisher eventPublisher;
  private CertificateAutoIssueService certificateAutoIssueService;
  private ExamAttemptServiceImpl service;

  @Test
  void submit_afterServerDeadline_forcesTimeoutReason() {
    ExamAttempt attempt = new ExamAttempt();
    attempt.setExamAttemptId(44L);
    attempt.setAttemptStatus("ANSWERING");
    attempt.setDeadlineTime(LocalDateTime.now().minusSeconds(1));
    when(examAttemptMapper.selectById(44L)).thenReturn(attempt);

    service.submit(44L, new SubmitAttemptRequest());

    assertThat(attempt.getSubmitReason()).isEqualTo("TIMEOUT");
  }

  @BeforeEach
  void setUp() {
    examMapper = mock(ExamMapper.class);
    examCandidateMapper = mock(ExamCandidateMapper.class);
    examPolicyMapper = mock(ExamPolicyMapper.class);
    examAttemptMapper = mock(ExamAttemptMapper.class);
    attemptQuestionMapper = mock(ExamAttemptQuestionMapper.class);
    attemptOptionMapper = mock(ExamAttemptOptionMapper.class);
    examAnswerMapper = mock(ExamAnswerMapper.class);
    examAnswerItemMapper = mock(ExamAnswerItemMapper.class);
    reviewRecordMapper = mock(ExamReviewRecordMapper.class);
    examReviewerMapper = mock(ExamReviewerMapper.class);
    scorePublishLogMapper = mock(ExamScorePublishLogMapper.class);
    anticheatEventMapper = mock(ExamAnticheatEventMapper.class);
    practiceResetMapper = mock(ExamPracticeResetMapper.class);
    paperVersionMapper = mock(PaperVersionMapper.class);
    paperVersionQuestionMapper = mock(PaperVersionQuestionMapper.class);
    questionVersionMapper = mock(QuestionVersionMapper.class);
    questionOptionMapper = mock(QuestionOptionMapper.class);
    blankAnswerMapper = mock(QuestionBlankAnswerMapper.class);
    assessmentExecutionService = mock(AssessmentExecutionService.class);
    candidatePoolService = mock(QuestionCandidatePoolService.class);
    executionQuestionPoolService = mock(AssessmentExecutionQuestionPoolService.class);
    courseLearningService = mock(CourseLearningService.class);
    eventPublisher = mock(ApplicationEventPublisher.class);
    AssessmentExecution execution = standaloneExecution();
    when(assessmentExecutionService.ensureStandalone(any())).thenReturn(execution);
    when(assessmentExecutionService.requireById(any())).thenReturn(execution);
    when(assessmentExecutionService.requireStandaloneByExamId(any())).thenReturn(execution);
    certificateAutoIssueService = mock(CertificateAutoIssueService.class);
    service = new ExamAttemptServiceImpl(
      examMapper, examCandidateMapper, examPolicyMapper, examAttemptMapper,
      attemptQuestionMapper, attemptOptionMapper, examAnswerMapper, examAnswerItemMapper,
      reviewRecordMapper, examReviewerMapper, scorePublishLogMapper, anticheatEventMapper, practiceResetMapper,
      paperVersionMapper, paperVersionQuestionMapper, questionVersionMapper,
      questionOptionMapper, blankAnswerMapper, new ObjectMapper(),
      mock(ExamCandidateProfileSnapshotAssembler.class), assessmentExecutionService, candidatePoolService,
      executionQuestionPoolService,
      courseLearningService, eventPublisher, certificateAutoIssueService, mock(AttemptKnowledgeSnapshotService.class));
  }

  @Test
  @DisplayName("start：冻结试卷题目快照")
  void start_freezesSnapshot() {
    Exam exam = publishedExam();
    when(examMapper.selectById(1L)).thenReturn(exam);
    when(examCandidateMapper.selectOne(any())).thenReturn(null);
    when(examCandidateMapper.insert(any(ExamCandidate.class))).thenAnswer(inv -> {
      ExamCandidate c = inv.getArgument(0);
      c.setExamCandidateId(20L);
      return 1;
    });
    when(examAttemptMapper.selectOne(any())).thenReturn(null);
    PaperVersion pv = new PaperVersion();
    pv.setPaperVersionId(30L);
    pv.setTotalScore(new BigDecimal("10"));
    when(paperVersionMapper.selectById(30L)).thenReturn(pv);
    when(examAttemptMapper.insert(any(ExamAttempt.class))).thenAnswer(inv -> {
      ExamAttempt a = inv.getArgument(0);
      a.setExamAttemptId(40L);
      return 1;
    });

    PaperVersionQuestion pq = new PaperVersionQuestion();
    pq.setQuestionId(5L);
    pq.setQuestionVersionId(50L);
    pq.setQuestionType("SINGLE");
    pq.setScore(new BigDecimal("10"));
    pq.setSortOrder(1);
    pq.setSourceBankId(9L);
    when(paperVersionQuestionMapper.selectList(any())).thenReturn(List.of(pq));

    QuestionVersion qv = new QuestionVersion();
    qv.setQuestionVersionId(50L);
    qv.setStem("stem");
    qv.setGradingMode("AUTO");
    qv.setQuestionType("SINGLE");
    when(questionVersionMapper.selectById(50L)).thenReturn(qv);
    when(questionOptionMapper.selectList(any())).thenReturn(List.of());
    when(blankAnswerMapper.selectList(any())).thenReturn(List.of());
    when(attemptQuestionMapper.insert(any(ExamAttemptQuestion.class))).thenAnswer(inv -> {
      ExamAttemptQuestion aq = inv.getArgument(0);
      aq.setAttemptQuestionId(60L);
      return 1;
    });
    when(attemptQuestionMapper.selectList(any())).thenReturn(List.of());

    StartAttemptRequest req = new StartAttemptRequest();
    req.setExamId(1L);
    req.setUserId(7L);
    req.setClientAttemptToken("tok-1");
    var vo = service.start(req);
    assertThat(vo.getExamAttemptId()).isEqualTo(40L);
    assertThat(vo.getAttemptStatus()).isEqualTo("ANSWERING");
    verify(examAttemptMapper).insert(argThat((ExamAttempt attempt) ->
                                               Integer.valueOf(3600).equals(attempt.getDurationSecondsSnapshot())
                                                 && new BigDecimal("6").compareTo(attempt.getPassScoreSnapshot()) == 0
                                                 && new BigDecimal("10").compareTo(attempt.getTotalScoreSnapshot()) == 0
                                                 && "IMPORT".equals(attempt.getQuestionOrderModeSnapshot())));
    verify(attemptQuestionMapper).insert(any(ExamAttemptQuestion.class));
  }

  @Test
  @DisplayName("start：按执行对象启动时仍拒绝草稿独立考试")
  void start_byExecutionId_rejectsDraftStandaloneExam() {
    AssessmentExecution execution = standaloneExecution();
    when(assessmentExecutionService.requireById(11L)).thenReturn(execution);
    Exam exam = publishedExam();
    exam.setPublishStatus("DRAFT");
    when(examMapper.selectById(1L)).thenReturn(exam);
    StartAttemptRequest request = new StartAttemptRequest();
    request.setAssessmentExecutionId(11L);
    request.setUserId(7L);
    request.setClientAttemptToken("draft-execution-token");

    assertThatThrownBy(() -> service.start(request))
      .isInstanceOfSatisfying(BusinessException.class, ex -> {
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getCode()).isEqualTo(7006);
      });
    verify(examCandidateMapper, never()).insert(any(ExamCandidate.class));
    verify(examAttemptMapper, never()).insert(any(ExamAttempt.class));
  }

  @Test
  @DisplayName("start：线下考试不支持在线作答，返回 7072")
  void start_offlineExam_rejectsOnlineAnswering() {
    AssessmentExecution offline = standaloneExecution();
    offline.setResourceType("OFFLINE_RESULT");
    offline.setResourceId(1L);
    when(assessmentExecutionService.ensureStandalone(any())).thenReturn(offline);
    when(examMapper.selectById(1L)).thenReturn(publishedExam());
    StartAttemptRequest request = new StartAttemptRequest();
    request.setExamId(1L);
    request.setUserId(7L);
    request.setClientAttemptToken("offline-attempt-token");

    assertThatThrownBy(() -> service.start(request))
      .isInstanceOfSatisfying(BusinessException.class, ex -> {
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getCode()).isEqualTo(7072);
      });
    verify(examCandidateMapper, never()).insert(any(ExamCandidate.class));
    verify(examAttemptMapper, never()).insert(any(ExamAttempt.class));
  }

  @Test
  @DisplayName("start：课程题库练习无需试卷版本并冻结全部已发布题目")
  void start_courseBankPractice_withoutPaperVersion() {
    AssessmentExecution execution = new AssessmentExecution();
    execution.setAssessmentExecutionId(22L);
    execution.setOrganizationId(1L);
    execution.setSourceType("COURSE_BANK_PRACTICE");
    execution.setSourceBusinessId(102L);
    execution.setCourseOfferingId(10L);
    execution.setCourseVersionId(20L);
    execution.setCourseItemId(102L);
    execution.setAssessmentKind("BANK_PRACTICE");
    execution.setResourceType("QUESTION_BANK");
    execution.setResourceId(9L);
    execution.setDurationSecondsSnapshot(0);
    execution.setTotalScoreSnapshot(BigDecimal.ZERO);
    execution.setPassScoreSnapshot(BigDecimal.ZERO);
    execution.setQuestionOrderModeSnapshot("IMPORT");
    execution.setMultiSelectScoringSnapshot("ALL_OR_NOTHING");
    when(assessmentExecutionService.requireById(22L)).thenReturn(execution);
    when(examCandidateMapper.selectOne(any())).thenReturn(null);
    when(examCandidateMapper.insert(any(ExamCandidate.class))).thenAnswer(invocation -> {
      ((ExamCandidate) invocation.getArgument(0)).setExamCandidateId(20L);
      return 1;
    });
    when(examAttemptMapper.selectOne(any())).thenReturn(null);
    when(examAttemptMapper.insert(any(ExamAttempt.class))).thenAnswer(invocation -> {
      ((ExamAttempt) invocation.getArgument(0)).setExamAttemptId(40L);
      return 1;
    });
    when(candidatePoolService.loadCandidates(9L)).thenReturn(List.of(drawCandidate(5L, 50L)));
    QuestionVersion version = new QuestionVersion();
    version.setQuestionVersionId(50L);
    version.setQuestionId(5L);
    version.setQuestionType("SINGLE");
    version.setStem("题干");
    version.setGradingMode("AUTO");
    version.setPublishedStatus("PUBLISHED");
    when(questionVersionMapper.selectById(50L)).thenReturn(version);
    when(questionOptionMapper.selectList(any())).thenReturn(List.of());
    when(blankAnswerMapper.selectList(any())).thenReturn(List.of());
    when(attemptQuestionMapper.selectList(any())).thenReturn(List.of());

    StartAttemptRequest request = new StartAttemptRequest();
    request.setAssessmentExecutionId(22L);
    request.setUserId(7L);
    request.setCourseOfferingId(10L);
    request.setCourseEnrollmentId(100L);
    request.setCourseItemId(102L);
    request.setClientAttemptToken("bank-token");
    var result = service.start(request);

    assertThat(result.getPaperVersionId()).isNull();
    assertThat(result.getResourceType()).isEqualTo("QUESTION_BANK");
    verify(attemptQuestionMapper).insert(argThat((ExamAttemptQuestion snapshot) ->
                                                   snapshot.getQuestionVersionId().equals(50L) && snapshot.getScore().compareTo(
                                                     BigDecimal.ONE) == 0));
    verify(examAttemptMapper).insert(argThat((ExamAttempt attempt) -> "PRACTICE".equals(attempt.getAttemptType())));
    verify(paperVersionQuestionMapper, never()).selectList(any());
  }

  @Test
  @DisplayName("start：新版培训题库练习从执行池抽 10 题且不设分值，并持久化种子")
  void start_trainingBankPractice_drawsFrozenRulesAndPersistsSeed() {
    AssessmentExecution execution = trainingBankExecution();
    execution.setPolicySnapshotJson(
      "{\"questionPoolHash\":\"frozen-hash\",\"drawRules\":[{\"questionType\":\"SINGLE\",\"drawCount\":10,\"scorePerQuestion\":10}]}");
    execution.setPassScoreSnapshot(new BigDecimal("60"));
    sealSnapshot(execution);
    when(assessmentExecutionService.requireById(33L)).thenReturn(execution);
    Exam bankExam = publishedExam();
    bankExam.setExamId(90L);
    bankExam.setExamType("BANK_PRACTICE");
    bankExam.setQuestionBankId(9L);
    when(examMapper.selectById(90L)).thenReturn(bankExam);
    when(examCandidateMapper.selectOne(any())).thenReturn(null);
    when(examCandidateMapper.insert(any(ExamCandidate.class))).thenAnswer(invocation -> {
      ((ExamCandidate) invocation.getArgument(0)).setExamCandidateId(20L);
      return 1;
    });
    when(examAttemptMapper.selectOne(any())).thenReturn(null);
    when(examAttemptMapper.insert(any(ExamAttempt.class))).thenAnswer(invocation -> {
      ((ExamAttempt) invocation.getArgument(0)).setExamAttemptId(40L);
      return 1;
    });
    List<com.sts.question.draw.QuestionDrawEngine.Candidate> questions = new ArrayList<>();
    for (long i = 1; i <= 12; i++) {
      questions.add(drawCandidate(i, 50L));
    }
    when(executionQuestionPoolService.loadCandidates(33L, "frozen-hash")).thenReturn(questions);
    when(questionVersionMapper.selectById(any())).thenAnswer(invocation -> {
      QuestionVersion version = new QuestionVersion();
      version.setQuestionVersionId(50L);
      version.setQuestionType("SINGLE");
      version.setDifficulty("EASY");
      version.setStem("题干");
      version.setGradingMode("AUTO");
      version.setPublishedStatus("PUBLISHED");
      return version;
    });
    when(questionOptionMapper.selectList(any())).thenReturn(List.of());
    when(blankAnswerMapper.selectList(any())).thenReturn(List.of());
    when(attemptQuestionMapper.selectList(any())).thenReturn(List.of());
    ArgumentCaptor<ExamAttemptQuestion> questionCaptor = ArgumentCaptor.forClass(ExamAttemptQuestion.class);
    ArgumentCaptor<ExamAttempt> attemptCaptor = ArgumentCaptor.forClass(ExamAttempt.class);

    StartAttemptRequest request = new StartAttemptRequest();
    request.setAssessmentExecutionId(33L);
    request.setUserId(7L);
    request.setTrainingParticipantId(2L);
    request.setClientAttemptToken("train-bank-token");
    service.start(request);

    verify(attemptQuestionMapper, times(10)).insert(questionCaptor.capture());
    assertThat(questionCaptor.getAllValues()).allMatch(q -> q.getScore().compareTo(new BigDecimal("10")) == 0);
    assertThat(questionCaptor.getAllValues()).allMatch(q -> Integer.valueOf(1).equals(q.getDrawRuleIndex())
      && "AUTO".equals(q.getEvaluationMode()));
    assertThat(questionCaptor.getAllValues().getFirst().getCorrectAnswerSnapshotJson()).contains("drawSeed");
    verify(examAttemptMapper, atLeastOnce()).updateById(attemptCaptor.capture());
    assertThat(attemptCaptor.getValue().getTotalScoreSnapshot()).isNull();
    assertThat(attemptCaptor.getValue().getPassScoreSnapshot()).isNull();
    assertThat(attemptCaptor.getValue().getDrawSeed()).isNotNull();
    verify(candidatePoolService, never()).loadCandidates(9L);
  }

  @Test
  @DisplayName("start：TRAINING_PRACTICE 缺少有效 drawRules 时拒绝开考")
  void start_trainingPractice_withoutDrawRules_rejected() {
    AssessmentExecution execution = trainingBankExecution();
    execution.setPolicySnapshotJson("{}");
    when(assessmentExecutionService.requireById(33L)).thenReturn(execution);
    Exam bankExam = publishedExam();
    bankExam.setExamId(90L);
    bankExam.setExamType("BANK_PRACTICE");
    bankExam.setQuestionBankId(9L);
    when(examMapper.selectById(90L)).thenReturn(bankExam);
    when(examCandidateMapper.selectOne(any())).thenReturn(null);
    when(examCandidateMapper.insert(any(ExamCandidate.class))).thenAnswer(invocation -> {
      ((ExamCandidate) invocation.getArgument(0)).setExamCandidateId(20L);
      return 1;
    });
    when(examAttemptMapper.selectOne(any())).thenReturn(null);
    when(examAttemptMapper.insert(any(ExamAttempt.class))).thenAnswer(invocation -> {
      ((ExamAttempt) invocation.getArgument(0)).setExamAttemptId(40L);
      return 1;
    });
    when(candidatePoolService.loadCandidates(9L)).thenReturn(List.of(drawCandidate(5L, 50L)));
    QuestionVersion version = new QuestionVersion();
    version.setQuestionVersionId(50L);
    version.setQuestionType("SINGLE");
    version.setStem("题干");
    version.setGradingMode("AUTO");
    version.setPublishedStatus("PUBLISHED");
    when(questionVersionMapper.selectById(50L)).thenReturn(version);

    StartAttemptRequest request = new StartAttemptRequest();
    request.setAssessmentExecutionId(33L);
    request.setUserId(7L);
    request.setClientAttemptToken("train-no-rules");

    assertThatThrownBy(() -> service.start(request))
      .isInstanceOfSatisfying(BusinessException.class, ex -> {
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getCode()).isEqualTo(7063);
      });
    verify(attemptQuestionMapper, never()).insert(any(ExamAttemptQuestion.class));
  }

  @Test
  @DisplayName("start：TRAINING_PRACTICE 的 policySnapshotJson 被修改但 snapshotHash 未同步时拒绝开考")
  void start_trainingPractice_rejectsTamperedPolicySnapshotHash() {
    when(assessmentExecutionService.requireById(33L)).thenThrow(
      new BusinessException(HttpStatus.BAD_REQUEST, 7063, "考核执行快照已损坏"));
    Exam bankExam = publishedExam();
    bankExam.setExamId(90L);
    bankExam.setExamType("BANK_PRACTICE");
    bankExam.setQuestionBankId(9L);
    when(examMapper.selectById(90L)).thenReturn(bankExam);

    StartAttemptRequest request = new StartAttemptRequest();
    request.setAssessmentExecutionId(33L);
    request.setUserId(7L);
    request.setClientAttemptToken("train-tampered-hash");

    assertThatThrownBy(() -> service.start(request))
      .isInstanceOfSatisfying(BusinessException.class, ex -> {
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getCode()).isEqualTo(7063);
      });
    verify(examCandidateMapper, never()).insert(any(ExamCandidate.class));
    verify(examAttemptMapper, never()).insert(any(ExamAttempt.class));
    verify(attemptQuestionMapper, never()).insert(any(ExamAttemptQuestion.class));
  }

  @Test
  @DisplayName("start：同一 Attempt 令牌重复进入不重新抽题")
  void start_sameToken_doesNotRedraw() {
    AssessmentExecution execution = trainingBankExecution();
    when(assessmentExecutionService.requireById(33L)).thenReturn(execution);
    Exam bankExam = publishedExam();
    bankExam.setExamId(90L);
    bankExam.setExamType("BANK_PRACTICE");
    when(examMapper.selectById(90L)).thenReturn(bankExam);
    ExamCandidate candidate = new ExamCandidate();
    candidate.setExamCandidateId(20L);
    candidate.setUserId(7L);
    when(examCandidateMapper.selectOne(any())).thenReturn(candidate);
    ExamAttempt existing = new ExamAttempt();
    existing.setExamAttemptId(40L);
    existing.setAssessmentExecutionId(33L);
    existing.setExamCandidateId(20L);
    existing.setClientAttemptToken("same-token");
    existing.setAttemptStatus("ANSWERING");
    when(examAttemptMapper.selectOne(any())).thenReturn(existing);
    when(attemptQuestionMapper.selectList(any())).thenReturn(List.of());
    when(examAnswerMapper.selectList(any())).thenReturn(List.of());

    StartAttemptRequest request = new StartAttemptRequest();
    request.setAssessmentExecutionId(33L);
    request.setUserId(7L);
    request.setClientAttemptToken("same-token");
    var result = service.start(request);

    assertThat(result.getExamAttemptId()).isEqualTo(40L);
    verify(candidatePoolService, never()).loadCandidates(any());
    verify(attemptQuestionMapper, never()).insert(any(ExamAttemptQuestion.class));
  }

  @Test
  @DisplayName("start：课程测验已有终态作答时必须复用，禁止创建第二份答卷")
  void start_courseQuiz_reusesTerminalAttempt() {
    AssessmentExecution execution = new AssessmentExecution();
    execution.setAssessmentExecutionId(22L);
    execution.setOrganizationId(1L);
    execution.setSourceType("COURSE_QUIZ");
    execution.setPolicySnapshotJson("{\"isFinalAssessment\":0}");
    execution.setCourseOfferingId(10L);
    execution.setCourseItemId(102L);
    execution.setAssessmentKind("QUIZ");
    execution.setResourceType("PAPER_VERSION");
    execution.setResourceId(30L);
    when(assessmentExecutionService.requireById(22L)).thenReturn(execution);
    ExamCandidate candidate = new ExamCandidate();
    candidate.setExamCandidateId(20L);
    candidate.setAttemptCount(1);
    when(examCandidateMapper.selectOne(any())).thenReturn(candidate);
    ExamAttempt terminal = new ExamAttempt();
    terminal.setExamAttemptId(40L);
    terminal.setExamCandidateId(20L);
    terminal.setAssessmentExecutionId(22L);
    terminal.setSourceType("COURSE_QUIZ");
    terminal.setAttemptStatus("PENDING_REVIEW");
    when(examAttemptMapper.selectOne(any())).thenReturn(null, null, terminal);
    when(attemptQuestionMapper.selectList(any())).thenReturn(List.of());

    StartAttemptRequest request = new StartAttemptRequest();
    request.setAssessmentExecutionId(22L);
    request.setUserId(7L);
    request.setCourseOfferingId(10L);
    request.setCourseEnrollmentId(100L);
    request.setCourseItemId(102L);
    request.setClientAttemptToken("quiz-retry-token");

    var result = service.start(request);

    assertThat(result.getExamAttemptId()).isEqualTo(40L);
    verify(examAttemptMapper, never()).insert(any(ExamAttempt.class));
  }

  @Test
  @DisplayName("start：结业首次生效失败允许补测一次并锁定考生")
  void start_finalQuiz_failureAllowsOnlyOneRetake() {
    AssessmentExecution execution = new AssessmentExecution();
    execution.setAssessmentExecutionId(22L);
    execution.setOrganizationId(1L);
    execution.setSourceType("COURSE_QUIZ");
    execution.setPolicySnapshotJson("{\"isFinalAssessment\":1}");
    execution.setCourseOfferingId(10L);
    execution.setCourseItemId(102L);
    execution.setAssessmentKind("QUIZ");
    execution.setResourceType("PAPER_VERSION");
    execution.setResourceId(30L);
    when(assessmentExecutionService.requireById(22L)).thenReturn(execution);
    ExamCandidate candidate = new ExamCandidate();
    candidate.setExamCandidateId(20L);
    candidate.setAttemptCount(1);
    when(examCandidateMapper.selectOne(any())).thenReturn(candidate);
    ExamAttempt terminal = new ExamAttempt();
    terminal.setExamAttemptId(40L);
    terminal.setExamCandidateId(20L);
    terminal.setAssessmentExecutionId(22L);
    terminal.setSourceType("COURSE_QUIZ");
    terminal.setAttemptStatus("RESULT_EFFECTIVE");
    terminal.setIsPassed(0);
    when(examAttemptMapper.selectOne(any())).thenReturn(null, null, terminal);
    when(attemptQuestionMapper.selectList(any())).thenReturn(List.of());

    StartAttemptRequest request = new StartAttemptRequest();
    request.setAssessmentExecutionId(22L);
    request.setUserId(7L);
    request.setCourseOfferingId(10L);
    request.setCourseEnrollmentId(100L);
    request.setCourseItemId(102L);
    request.setClientAttemptToken("quiz-retry-token");

    var result = service.start(request);

    verify(examAttemptMapper).insert(argThat((ExamAttempt attempt) -> attempt.getAttemptNo() == 2
      && "RETAKE".equals(attempt.getAttemptType())));
    verify(courseLearningService).assertFinalAssessmentReady(100L, 102L, 7L);
    verify(examCandidateMapper, times(2)).selectOne(any());
  }

  @Test
  @DisplayName("start：结业pendingReview不得创建补测")
  void start_finalQuiz_pendingReviewDoesNotCreateRetake() {
    AssessmentExecution execution = new AssessmentExecution();
    execution.setAssessmentExecutionId(22L);
    execution.setOrganizationId(1L);
    execution.setSourceType("COURSE_QUIZ");
    execution.setPolicySnapshotJson("{\"isFinalAssessment\":1}");
    execution.setCourseOfferingId(10L);
    execution.setCourseItemId(102L);
    execution.setAssessmentKind("QUIZ");
    execution.setResourceType("PAPER_VERSION");
    execution.setResourceId(30L);
    when(assessmentExecutionService.requireById(22L)).thenReturn(execution);
    ExamCandidate candidate = new ExamCandidate();
    candidate.setExamCandidateId(20L);
    candidate.setAttemptCount(1);
    when(examCandidateMapper.selectOne(any())).thenReturn(candidate);
    ExamAttempt terminal = new ExamAttempt();
    terminal.setExamAttemptId(40L);
    terminal.setExamCandidateId(20L);
    terminal.setAssessmentExecutionId(22L);
    terminal.setSourceType("COURSE_QUIZ");
    terminal.setAttemptStatus("PENDING_REVIEW");
    terminal.setIsPassed(0);
    when(examAttemptMapper.selectOne(any())).thenReturn(null, null, terminal);
    when(attemptQuestionMapper.selectList(any())).thenReturn(List.of());

    StartAttemptRequest request = new StartAttemptRequest();
    request.setAssessmentExecutionId(22L);
    request.setUserId(7L);
    request.setCourseOfferingId(10L);
    request.setCourseEnrollmentId(100L);
    request.setCourseItemId(102L);
    request.setClientAttemptToken("quiz-retry-token");

    var result = service.start(request);

    assertThat(result.getExamAttemptId()).isEqualTo(40L);
    verify(examAttemptMapper, never()).insert(any(ExamAttempt.class));
    verify(courseLearningService).assertFinalAssessmentReady(100L, 102L, 7L);
    verify(examCandidateMapper, times(2)).selectOne(any());
  }

  @Test
  @DisplayName("start：结业passed不得创建补测")
  void start_finalQuiz_passedDoesNotCreateRetake() {
    AssessmentExecution execution = new AssessmentExecution();
    execution.setAssessmentExecutionId(22L);
    execution.setOrganizationId(1L);
    execution.setSourceType("COURSE_QUIZ");
    execution.setPolicySnapshotJson("{\"isFinalAssessment\":1}");
    execution.setCourseOfferingId(10L);
    execution.setCourseItemId(102L);
    execution.setAssessmentKind("QUIZ");
    execution.setResourceType("PAPER_VERSION");
    execution.setResourceId(30L);
    when(assessmentExecutionService.requireById(22L)).thenReturn(execution);
    ExamCandidate candidate = new ExamCandidate();
    candidate.setExamCandidateId(20L);
    candidate.setAttemptCount(1);
    when(examCandidateMapper.selectOne(any())).thenReturn(candidate);
    ExamAttempt terminal = new ExamAttempt();
    terminal.setExamAttemptId(40L);
    terminal.setExamCandidateId(20L);
    terminal.setAssessmentExecutionId(22L);
    terminal.setSourceType("COURSE_QUIZ");
    terminal.setAttemptStatus("RESULT_EFFECTIVE");
    terminal.setIsPassed(1);
    when(examAttemptMapper.selectOne(any())).thenReturn(null, null, terminal);
    when(attemptQuestionMapper.selectList(any())).thenReturn(List.of());

    StartAttemptRequest request = new StartAttemptRequest();
    request.setAssessmentExecutionId(22L);
    request.setUserId(7L);
    request.setCourseOfferingId(10L);
    request.setCourseEnrollmentId(100L);
    request.setCourseItemId(102L);
    request.setClientAttemptToken("quiz-retry-token");

    var result = service.start(request);

    assertThat(result.getExamAttemptId()).isEqualTo(40L);
    verify(examAttemptMapper, never()).insert(any(ExamAttempt.class));
    verify(courseLearningService).assertFinalAssessmentReady(100L, 102L, 7L);
    verify(examCandidateMapper, times(2)).selectOne(any());
  }

  @Test
  @DisplayName("start：结业exhausted不得创建补测")
  void start_finalQuiz_exhaustedDoesNotCreateRetake() {
    AssessmentExecution execution = new AssessmentExecution();
    execution.setAssessmentExecutionId(22L);
    execution.setOrganizationId(1L);
    execution.setSourceType("COURSE_QUIZ");
    execution.setPolicySnapshotJson("{\"isFinalAssessment\":1}");
    execution.setCourseOfferingId(10L);
    execution.setCourseItemId(102L);
    execution.setAssessmentKind("QUIZ");
    execution.setResourceType("PAPER_VERSION");
    execution.setResourceId(30L);
    when(assessmentExecutionService.requireById(22L)).thenReturn(execution);
    ExamCandidate candidate = new ExamCandidate();
    candidate.setExamCandidateId(20L);
    candidate.setAttemptCount(2);
    when(examCandidateMapper.selectOne(any())).thenReturn(candidate);
    ExamAttempt terminal = new ExamAttempt();
    terminal.setExamAttemptId(40L);
    terminal.setExamCandidateId(20L);
    terminal.setAssessmentExecutionId(22L);
    terminal.setSourceType("COURSE_QUIZ");
    terminal.setAttemptStatus("RESULT_EFFECTIVE");
    terminal.setIsPassed(0);
    when(examAttemptMapper.selectOne(any())).thenReturn(null, null, terminal);
    when(attemptQuestionMapper.selectList(any())).thenReturn(List.of());

    StartAttemptRequest request = new StartAttemptRequest();
    request.setAssessmentExecutionId(22L);
    request.setUserId(7L);
    request.setCourseOfferingId(10L);
    request.setCourseEnrollmentId(100L);
    request.setCourseItemId(102L);
    request.setClientAttemptToken("quiz-retry-token");

    var result = service.start(request);

    assertThat(result.getExamAttemptId()).isEqualTo(40L);
    verify(examAttemptMapper, never()).insert(any(ExamAttempt.class));
    verify(courseLearningService).assertFinalAssessmentReady(100L, 102L, 7L);
    verify(examCandidateMapper, times(2)).selectOne(any());
  }

  @Test
  @DisplayName("start：培训考试已有终态时忽略请求 FIRST，禁止第二份首次答卷")
  void start_trainingExam_reusesTerminalAttempt() {
    AssessmentExecution execution = new AssessmentExecution();
    execution.setAssessmentExecutionId(11L);
    execution.setOrganizationId(1L);
    execution.setSourceType("TRAINING_EXAM");
    execution.setExamId(1L);
    execution.setAssessmentKind("FORMAL_EXAM");
    execution.setResourceType("PAPER_VERSION");
    execution.setResourceId(30L);
    execution.setDurationSecondsSnapshot(3600);
    execution.setTotalScoreSnapshot(new BigDecimal("10"));
    execution.setPassScoreSnapshot(new BigDecimal("6"));
    execution.setQuestionOrderModeSnapshot("IMPORT");
    sealSnapshot(execution);
    when(assessmentExecutionService.requireById(11L)).thenReturn(execution);
    when(examMapper.selectById(1L)).thenReturn(publishedExam());
    ExamCandidate candidate = new ExamCandidate();
    candidate.setExamCandidateId(20L);
    candidate.setTrainingParticipantId(2L);
    candidate.setAttemptCount(1);
    when(examCandidateMapper.selectOne(any())).thenReturn(candidate);
    ExamAttempt terminal = new ExamAttempt();
    terminal.setExamAttemptId(40L);
    terminal.setExamCandidateId(20L);
    terminal.setAttemptStatus("PENDING_PUBLISH");
    when(examAttemptMapper.selectOne(any())).thenReturn(null, null, terminal);
    when(attemptQuestionMapper.selectList(any())).thenReturn(List.of());

    StartAttemptRequest request = new StartAttemptRequest();
    request.setAssessmentExecutionId(11L);
    request.setUserId(7L);
    request.setClientAttemptToken("new-token");
    request.setAttemptType("FIRST");

    var result = service.start(request);

    assertThat(result.getExamAttemptId()).isEqualTo(40L);
    verify(examAttemptMapper, never()).insert(any(ExamAttempt.class));
  }

  @Test
  @DisplayName("start：历史未归属候选人从培训入口恢复终态时补齐参训人绑定")
  void start_trainingExam_bindsLegacyUnassignedCandidate() {
    AssessmentExecution execution = new AssessmentExecution();
    execution.setAssessmentExecutionId(11L);
    execution.setOrganizationId(1L);
    execution.setSourceType("TRAINING_EXAM");
    execution.setExamId(1L);
    execution.setAssessmentKind("FORMAL_EXAM");
    execution.setResourceType("PAPER_VERSION");
    execution.setResourceId(30L);
    execution.setDurationSecondsSnapshot(3600);
    execution.setTotalScoreSnapshot(new BigDecimal("10"));
    execution.setPassScoreSnapshot(new BigDecimal("6"));
    execution.setQuestionOrderModeSnapshot("IMPORT");
    sealSnapshot(execution);
    when(assessmentExecutionService.requireById(11L)).thenReturn(execution);
    when(examMapper.selectById(1L)).thenReturn(publishedExam());

    ExamCandidate candidate = new ExamCandidate();
    candidate.setExamCandidateId(20L);
    candidate.setAttemptCount(1);
    when(examCandidateMapper.selectOne(any())).thenReturn(candidate);
    ExamAttempt terminal = new ExamAttempt();
    terminal.setExamAttemptId(40L);
    terminal.setExamCandidateId(20L);
    terminal.setAttemptStatus("PUBLISHED");
    when(examAttemptMapper.selectOne(any())).thenReturn(null, null, terminal);
    when(attemptQuestionMapper.selectList(any())).thenReturn(List.of());

    StartAttemptRequest request = new StartAttemptRequest();
    request.setAssessmentExecutionId(11L);
    request.setUserId(7L);
    request.setTrainingParticipantId(9L);
    request.setClientAttemptToken("legacy-training-token");

    var result = service.start(request);

    assertThat(result.getExamAttemptId()).isEqualTo(40L);
    assertThat(candidate.getTrainingParticipantId()).isEqualTo(9L);
    assertThat(candidate.getAssignmentSource()).isEqualTo("TRAINING");
    verify(examCandidateMapper).updateById(candidate);
  }

  @Test
  @DisplayName("start：历史纯客观题培训考试待发布时自动公布并刷新投影")
  void start_trainingExam_promotesLegacyObjectivePendingPublishResult() {
    AssessmentExecution execution = new AssessmentExecution();
    execution.setAssessmentExecutionId(11L);
    execution.setOrganizationId(1L);
    execution.setSourceType("TRAINING_EXAM");
    execution.setExamId(1L);
    execution.setAssessmentKind("FORMAL_EXAM");
    execution.setResourceType("PAPER_VERSION");
    execution.setResourceId(30L);
    execution.setDurationSecondsSnapshot(3600);
    execution.setTotalScoreSnapshot(new BigDecimal("10"));
    execution.setPassScoreSnapshot(new BigDecimal("6"));
    execution.setQuestionOrderModeSnapshot("IMPORT");
    sealSnapshot(execution);
    Exam exam = publishedExam();
    exam.setPlanContentId(30L);
    when(assessmentExecutionService.requireById(11L)).thenReturn(execution);
    when(examMapper.selectById(1L)).thenReturn(exam);

    ExamCandidate candidate = new ExamCandidate();
    candidate.setExamCandidateId(20L);
    candidate.setAssessmentExecutionId(11L);
    candidate.setTrainingParticipantId(9L);
    candidate.setAssignmentSource("TRAINING");
    candidate.setAttemptCount(1);
    when(examCandidateMapper.selectOne(any())).thenReturn(candidate);
    ExamAttempt terminal = new ExamAttempt();
    terminal.setExamAttemptId(40L);
    terminal.setExamCandidateId(20L);
    terminal.setAssessmentExecutionId(11L);
    terminal.setSourceType("TRAINING_EXAM");
    terminal.setAttemptStatus("PENDING_PUBLISH");
    terminal.setFinalScore(new BigDecimal("10"));
    terminal.setPassScoreSnapshot(new BigDecimal("6"));
    when(examAttemptMapper.selectOne(any())).thenReturn(null, null, terminal);
    when(examAnswerMapper.selectCount(any())).thenReturn(0L);
    when(examPolicyMapper.selectOne(any())).thenReturn(null);
    when(attemptQuestionMapper.selectList(any())).thenReturn(List.of());

    StartAttemptRequest request = new StartAttemptRequest();
    request.setAssessmentExecutionId(11L);
    request.setUserId(7L);
    request.setTrainingParticipantId(9L);
    request.setClientAttemptToken("legacy-objective-token");

    var result = service.start(request);

    assertThat(result.getAttemptStatus()).isEqualTo("PUBLISHED");
    assertThat(candidate.getCandidateStatus()).isEqualTo("PASSED");
    assertTrainingProjectionEvent(9L, 30L);
  }

  @Test
  @DisplayName("start：历史培训考试存在人工评分记录时保持待发布")
  void start_trainingExam_keepsManualReviewedPendingPublishResult() {
    AssessmentExecution execution = new AssessmentExecution();
    execution.setAssessmentExecutionId(11L);
    execution.setOrganizationId(1L);
    execution.setSourceType("TRAINING_EXAM");
    execution.setExamId(1L);
    execution.setAssessmentKind("FORMAL_EXAM");
    execution.setResourceType("PAPER_VERSION");
    execution.setResourceId(30L);
    execution.setDurationSecondsSnapshot(3600);
    execution.setTotalScoreSnapshot(new BigDecimal("10"));
    execution.setPassScoreSnapshot(new BigDecimal("6"));
    execution.setQuestionOrderModeSnapshot("IMPORT");
    sealSnapshot(execution);
    when(assessmentExecutionService.requireById(11L)).thenReturn(execution);
    when(examMapper.selectById(1L)).thenReturn(publishedExam());

    ExamCandidate candidate = new ExamCandidate();
    candidate.setExamCandidateId(20L);
    candidate.setTrainingParticipantId(9L);
    candidate.setAssignmentSource("TRAINING");
    candidate.setAttemptCount(1);
    when(examCandidateMapper.selectOne(any())).thenReturn(candidate);
    ExamAttempt terminal = new ExamAttempt();
    terminal.setExamAttemptId(40L);
    terminal.setExamCandidateId(20L);
    terminal.setSourceType("TRAINING_EXAM");
    terminal.setAttemptStatus("PENDING_PUBLISH");
    terminal.setFinalScore(new BigDecimal("10"));
    terminal.setPassScoreSnapshot(new BigDecimal("6"));
    when(examAttemptMapper.selectOne(any())).thenReturn(null, null, terminal);
    when(examAnswerMapper.selectCount(any())).thenReturn(0L, 1L);
    when(attemptQuestionMapper.selectList(any())).thenReturn(List.of());

    StartAttemptRequest request = new StartAttemptRequest();
    request.setAssessmentExecutionId(11L);
    request.setUserId(7L);
    request.setTrainingParticipantId(9L);
    request.setClientAttemptToken("legacy-manual-token");

    var result = service.start(request);

    assertThat(result.getAttemptStatus()).isEqualTo("PENDING_PUBLISH");
    verify(examAttemptMapper, never()).updateById(terminal);
  }

  @Test
  @DisplayName("start：培训题库已有终态时不得再开首次作答")
  void start_trainingPractice_reusesTerminalAttempt() {
    AssessmentExecution execution = trainingBankExecution();
    when(assessmentExecutionService.requireById(33L)).thenReturn(execution);
    Exam bankExam = publishedExam();
    bankExam.setExamId(90L);
    bankExam.setExamType("BANK_PRACTICE");
    when(examMapper.selectById(90L)).thenReturn(bankExam);
    ExamCandidate candidate = new ExamCandidate();
    candidate.setExamCandidateId(20L);
    candidate.setAttemptCount(1);
    when(examCandidateMapper.selectOne(any())).thenReturn(candidate);
    ExamAttempt terminal = new ExamAttempt();
    terminal.setExamAttemptId(41L);
    terminal.setExamCandidateId(20L);
    terminal.setAttemptStatus("PUBLISHED");
    when(examAttemptMapper.selectOne(any())).thenReturn(null, null, terminal);
    when(attemptQuestionMapper.selectList(any())).thenReturn(List.of());

    StartAttemptRequest request = new StartAttemptRequest();
    request.setAssessmentExecutionId(33L);
    request.setUserId(7L);
    request.setClientAttemptToken("bank-retry");
    request.setAttemptType("FIRST");

    var result = service.start(request);

    assertThat(result.getExamAttemptId()).isEqualTo(41L);
    verify(examAttemptMapper, never()).insert(any(ExamAttempt.class));
  }

  @Test
  @DisplayName("start：新版培训题库练习未达到最大次数时可用新令牌开启新一轮")
  void start_trainingPractice_createsNewRoundBeforeMaxAttempts() {
    AssessmentExecution execution = trainingBankExecution();
    execution.setPolicySnapshotJson(
      "{\"questionPoolHash\":\"frozen-hash\",\"maxAttempts\":2,"
        + "\"drawRules\":[{\"questionType\":\"SINGLE\",\"drawCount\":1}]}"
    );
    sealSnapshot(execution);
    when(assessmentExecutionService.requireById(33L)).thenReturn(execution);
    Exam bankExam = publishedExam();
    bankExam.setExamId(90L);
    bankExam.setExamType("BANK_PRACTICE");
    when(examMapper.selectById(90L)).thenReturn(bankExam);
    ExamCandidate candidate = new ExamCandidate();
    candidate.setExamCandidateId(20L);
    candidate.setAttemptCount(1);
    when(examCandidateMapper.selectOne(any())).thenReturn(candidate);
    ExamAttempt terminal = new ExamAttempt();
    terminal.setExamAttemptId(41L);
    terminal.setExamCandidateId(20L);
    terminal.setAttemptStatus("RESULT_EFFECTIVE");
    when(examAttemptMapper.selectOne(any())).thenReturn(null, null, terminal);
    when(examAttemptMapper.insert(any(ExamAttempt.class))).thenAnswer(invocation -> {
      ((ExamAttempt) invocation.getArgument(0)).setExamAttemptId(42L);
      return 1;
    });
    when(executionQuestionPoolService.loadCandidates(33L, "frozen-hash"))
      .thenReturn(List.of(drawCandidate(5L, 50L)));
    QuestionVersion version = new QuestionVersion();
    version.setQuestionVersionId(50L);
    version.setQuestionId(5L);
    version.setQuestionType("SINGLE");
    version.setStem("题干");
    version.setGradingMode("AUTO");
    version.setPublishedStatus("PUBLISHED");
    when(questionVersionMapper.selectById(50L)).thenReturn(version);
    when(questionOptionMapper.selectList(any())).thenReturn(List.of());
    when(blankAnswerMapper.selectList(any())).thenReturn(List.of());
    when(attemptQuestionMapper.selectList(any())).thenReturn(List.of());

    StartAttemptRequest request = new StartAttemptRequest();
    request.setAssessmentExecutionId(33L);
    request.setUserId(7L);
    request.setClientAttemptToken("training-round-2");

    var result = service.start(request);

    assertThat(result.getExamAttemptId()).isEqualTo(42L);
    verify(examAttemptMapper).insert(argThat((ExamAttempt attempt) -> attempt.getAttemptNo() == 2
      && "PRACTICE".equals(attempt.getAttemptType())));
    verify(examAttemptMapper, atLeastOnce()).updateById(argThat((ExamAttempt attempt) ->
                                                                  attempt.getDrawSeed() != null));
  }

  @Test
  @DisplayName("start：通用开考不得凭请求 RETAKE 创建补考")
  void start_ignoresRequestedRetakeType() {
    AssessmentExecution execution = standaloneExecution();
    when(assessmentExecutionService.requireById(11L)).thenReturn(execution);
    when(examMapper.selectById(1L)).thenReturn(publishedExam());
    ExamCandidate candidate = new ExamCandidate();
    candidate.setExamCandidateId(20L);
    candidate.setAttemptCount(1);
    when(examCandidateMapper.selectOne(any())).thenReturn(candidate);
    ExamAttempt terminal = new ExamAttempt();
    terminal.setExamAttemptId(40L);
    terminal.setExamCandidateId(20L);
    terminal.setAttemptStatus("PUBLISHED");
    when(examAttemptMapper.selectOne(any())).thenReturn(null, null, terminal);
    when(attemptQuestionMapper.selectList(any())).thenReturn(List.of());

    StartAttemptRequest request = new StartAttemptRequest();
    request.setAssessmentExecutionId(11L);
    request.setUserId(7L);
    request.setClientAttemptToken("retake-via-start");
    request.setAttemptType("RETAKE");

    var result = service.start(request);

    assertThat(result.getExamAttemptId()).isEqualTo(40L);
    verify(examAttemptMapper, never()).insert(any(ExamAttempt.class));
  }

  @Test
  @DisplayName("start：课程题库无进行中时可再开 PRACTICE")
  void start_courseBankPractice_createsPracticeAfterTerminal() {
    AssessmentExecution execution = new AssessmentExecution();
    execution.setAssessmentExecutionId(22L);
    execution.setOrganizationId(1L);
    execution.setSourceType("COURSE_BANK_PRACTICE");
    execution.setCourseOfferingId(10L);
    execution.setCourseItemId(102L);
    execution.setAssessmentKind("BANK_PRACTICE");
    execution.setResourceType("QUESTION_BANK");
    execution.setResourceId(9L);
    execution.setDurationSecondsSnapshot(0);
    execution.setTotalScoreSnapshot(BigDecimal.ZERO);
    execution.setPassScoreSnapshot(BigDecimal.ZERO);
    execution.setQuestionOrderModeSnapshot("IMPORT");
    execution.setMultiSelectScoringSnapshot("ALL_OR_NOTHING");
    when(assessmentExecutionService.requireById(22L)).thenReturn(execution);
    ExamCandidate candidate = new ExamCandidate();
    candidate.setExamCandidateId(20L);
    candidate.setAttemptCount(1);
    when(examCandidateMapper.selectOne(any())).thenReturn(candidate);
    ExamAttempt terminal = new ExamAttempt();
    terminal.setExamAttemptId(39L);
    terminal.setExamCandidateId(20L);
    terminal.setAttemptStatus("RESULT_EFFECTIVE");
    when(examAttemptMapper.selectOne(any())).thenReturn(null, null, terminal);
    when(examAttemptMapper.insert(any(ExamAttempt.class))).thenAnswer(invocation -> {
      ((ExamAttempt) invocation.getArgument(0)).setExamAttemptId(40L);
      return 1;
    });
    when(candidatePoolService.loadCandidates(9L)).thenReturn(List.of(drawCandidate(5L, 50L)));
    QuestionVersion version = new QuestionVersion();
    version.setQuestionVersionId(50L);
    version.setQuestionId(5L);
    version.setQuestionType("SINGLE");
    version.setStem("题干");
    version.setGradingMode("AUTO");
    version.setPublishedStatus("PUBLISHED");
    when(questionVersionMapper.selectById(50L)).thenReturn(version);
    when(questionOptionMapper.selectList(any())).thenReturn(List.of());
    when(blankAnswerMapper.selectList(any())).thenReturn(List.of());
    when(attemptQuestionMapper.selectList(any())).thenReturn(List.of());

    StartAttemptRequest request = new StartAttemptRequest();
    request.setAssessmentExecutionId(22L);
    request.setUserId(7L);
    request.setCourseOfferingId(10L);
    request.setCourseEnrollmentId(100L);
    request.setCourseItemId(102L);
    request.setClientAttemptToken("bank-retry-token");

    var result = service.start(request);

    assertThat(result.getExamAttemptId()).isEqualTo(40L);
    verify(examAttemptMapper).insert(argThat((ExamAttempt attempt) -> "PRACTICE".equals(attempt.getAttemptType())));
  }

  @Test
  void finalCandidateInsertConflictReadsLatestWinner() throws Exception {
    com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(new org.apache.ibatis.builder.MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(), "batch6-current-read"), ExamCandidate.class);
    var execution = new AssessmentExecution(); execution.setAssessmentExecutionId(22L);
    execution.setCourseOfferingId(10L); execution.setSourceType("COURSE_QUIZ");
    execution.setPolicySnapshotJson("{\"isFinalAssessment\":1}");
    var winner = new ExamCandidate(); winner.setExamCandidateId(20L);
    when(examCandidateMapper.selectOne(any())).thenAnswer(inv -> {
      var query = (com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ExamCandidate>) inv.getArgument(0);
      return query.getSqlSegment().contains("FOR UPDATE") ? winner : null;
    });
    when(examCandidateMapper.insert(any(ExamCandidate.class))).thenThrow(new DuplicateKeyException("concurrent"));
    var request = new StartAttemptRequest(); request.setUserId(7L); request.setCourseOfferingId(10L);
    request.setCourseEnrollmentId(100L); request.setCourseItemId(102L);
    var method = ExamAttemptServiceImpl.class.getDeclaredMethod("findOrCreateCandidate", AssessmentExecution.class, StartAttemptRequest.class);
    method.setAccessible(true);
    assertThat(method.invoke(service, execution, request)).isSameAs(winner);
  }

  @Test
  @DisplayName("start：并发创建同一候选人时复用唯一键胜出的记录")
  void start_recoversCandidateAfterConcurrentInsert() {
    Exam exam = publishedExam();
    when(examMapper.selectById(1L)).thenReturn(exam);
    ExamCandidate winner = new ExamCandidate();
    winner.setExamCandidateId(20L);
    winner.setAssessmentExecutionId(10L);
    winner.setUserId(7L);
    winner.setAttemptCount(0);
    when(examCandidateMapper.selectOne(any())).thenReturn(null, winner);
    when(examCandidateMapper.insert(any(ExamCandidate.class)))
      .thenThrow(new DuplicateKeyException("concurrent candidate"));
    when(examAttemptMapper.selectOne(any())).thenReturn(null);
    when(examAttemptMapper.insert(any(ExamAttempt.class))).thenAnswer(invocation -> {
      ((ExamAttempt) invocation.getArgument(0)).setExamAttemptId(40L);
      return 1;
    });
    when(paperVersionQuestionMapper.selectList(any())).thenReturn(List.of());
    when(attemptQuestionMapper.selectList(any())).thenReturn(List.of());

    StartAttemptRequest request = new StartAttemptRequest();
    request.setExamId(1L);
    request.setUserId(7L);
    request.setClientAttemptToken("candidate-race-token");

    var result = service.start(request);

    assertThat(result.getExamCandidateId()).isEqualTo(20L);
    verify(examAttemptMapper).insert(argThat((ExamAttempt attempt) ->
                                               attempt.getExamCandidateId().equals(20L)));
  }

  @Test
  @DisplayName("get：管理端作答详情带回标准答案、已暂存答案及答案项")
  void get_includesSavedAnswers() {
    ExamAttempt attempt = answeringAttempt();
    when(examAttemptMapper.selectById(40L)).thenReturn(attempt);

    ExamAttemptQuestion question = new ExamAttemptQuestion();
    question.setAttemptQuestionId(60L);
    question.setExamAttemptId(40L);
    question.setQuestionType("MULTIPLE");
    question.setCorrectAnswerSnapshotJson("{\"standardAnswer\":\"A,B\",\"correctOptions\":[\"A\",\"B\"]}");
    when(attemptQuestionMapper.selectList(any())).thenReturn(List.of(question));
    when(attemptOptionMapper.selectList(any())).thenReturn(List.of());

    ExamAnswer answer = new ExamAnswer();
    answer.setExamAnswerId(70L);
    answer.setExamAttemptId(40L);
    answer.setAttemptQuestionId(60L);
    answer.setAnswerText("A,B");
    answer.setIsAnswered(1);
    answer.setFinalScore(new BigDecimal("10"));
    when(examAnswerMapper.selectList(any())).thenReturn(List.of(answer));
    ExamAnswerItem item = new ExamAnswerItem();
    item.setExamAnswerId(70L);
    item.setItemType("OPTION");
    item.setItemKey("A");
    when(examAnswerItemMapper.selectList(any())).thenReturn(List.of(item));

    var vo = service.get(40L);

    assertThat(vo.getQuestions()).hasSize(1);
    assertThat(vo.getQuestions().getFirst().getStandardAnswer()).isEqualTo("A,B");
    assertThat(vo.getQuestions().getFirst().getAnswer()).isNotNull();
    assertThat(vo.getQuestions().getFirst().getAnswer().getAnswerText()).isEqualTo("A,B");
    assertThat(vo.getQuestions().getFirst().getAnswer().getFinalScore()).isEqualByComparingTo("10");
    assertThat(vo.getQuestions().getFirst().getAnswer().getItems()).hasSize(1);
    assertThat(vo.getQuestions().getFirst().getAnswer().getItems().getFirst().getItemKey()).isEqualTo("A");
    assertThat(vo.getScorePublishStatus()).isEqualTo("UNPUBLISHED");
  }

  @Test
  @DisplayName("get：已发布尝试返回 scorePublishStatus=PUBLISHED")
  void get_publishedAttempt_exposesScorePublishStatus() {
    ExamAttempt attempt = answeringAttempt();
    attempt.setAttemptStatus("PUBLISHED");
    attempt.setScorePublishedTime(java.time.LocalDateTime.now());
    when(examAttemptMapper.selectById(40L)).thenReturn(attempt);
    when(attemptQuestionMapper.selectList(any())).thenReturn(List.of());

    var vo = service.get(40L);

    assertThat(vo.getScorePublishStatus()).isEqualTo("PUBLISHED");
    assertThat(vo.getScorePublishedTime()).isEqualTo(attempt.getScorePublishedTime());
  }

  @Test
  @DisplayName("submit：客观题自动判分；幂等再次提交返回终态")
  void submit_autoGrade_andIdempotent() {
    ExamAttempt attempt = answeringAttempt();
    when(examAttemptMapper.selectById(40L)).thenReturn(attempt);

    ExamAttemptQuestion aq = new ExamAttemptQuestion();
    aq.setAttemptQuestionId(60L);
    aq.setExamAttemptId(40L);
    aq.setQuestionType("SINGLE");
    aq.setGradingMode("AUTO");
    aq.setScore(new BigDecimal("10"));
    aq.setCorrectAnswerSnapshotJson("{\"correctOptions\":[\"A\"]}");
    when(attemptQuestionMapper.selectList(any())).thenReturn(List.of(aq));
    when(attemptQuestionMapper.selectById(60L)).thenReturn(aq);

    ExamAnswer answer = new ExamAnswer();
    answer.setExamAnswerId(70L);
    answer.setExamAttemptId(40L);
    answer.setAttemptQuestionId(60L);
    answer.setAnswerText("A");
    answer.setIsAnswered(1);
    answer.setFinalScore(BigDecimal.ZERO);
    when(examAnswerMapper.selectOne(any())).thenReturn(answer);
    when(examAnswerMapper.selectList(any())).thenReturn(List.of(answer));
    when(examAnswerItemMapper.selectList(any())).thenReturn(List.of());

    ExamAttemptOption opt = new ExamAttemptOption();
    opt.setOptionKey("A");
    opt.setIsCorrectSnapshot(1);
    when(attemptOptionMapper.selectList(any())).thenReturn(List.of(opt));
    when(examCandidateMapper.selectById(20L)).thenReturn(new ExamCandidate());

    var vo = service.submit(40L, new SubmitAttemptRequest());
    assertThat(vo.getAttemptStatus()).isEqualTo("PENDING_PUBLISH");
    assertThat(answer.getGradingStatus()).isEqualTo("AUTO_GRADED");
    assertThat(answer.getFinalScore()).isEqualByComparingTo("10");
    assertThat(answer.getIsAnswered()).isEqualTo(1);
    assertThat(answer.getAnswerText()).isEqualTo("A");

    attempt.setAttemptStatus("PENDING_PUBLISH");
    var again = service.submit(40L, new SubmitAttemptRequest());
    assertThat(again.getAttemptStatus()).isEqualTo("PENDING_PUBLISH");
    verify(eventPublisher, never()).publishEvent(any(Object.class));
  }

  @Test
  @DisplayName("submit：无主观题的培训正式考试自动公布成绩并刷新培训投影")
  void submit_objectiveTrainingExam_publishesScoreAndRefreshesProjection() {
    ExamAttempt attempt = answeringAttempt();
    attempt.setSourceType("TRAINING_EXAM");
    when(examAttemptMapper.selectById(40L)).thenReturn(attempt);

    ExamAttemptQuestion question = attemptQuestion(60L, "SINGLE", "AUTO");
    when(attemptQuestionMapper.selectList(any())).thenReturn(List.of(question));
    when(attemptQuestionMapper.selectById(60L)).thenReturn(question);

    ExamAnswer answer = new ExamAnswer();
    answer.setExamAnswerId(70L);
    answer.setExamAttemptId(40L);
    answer.setAttemptQuestionId(60L);
    answer.setAnswerText("A");
    answer.setIsAnswered(1);
    when(examAnswerMapper.selectOne(any())).thenReturn(answer);
    when(examAnswerMapper.selectList(any())).thenReturn(List.of(answer));
    when(examAnswerItemMapper.selectList(any())).thenReturn(List.of());

    ExamAttemptOption option = new ExamAttemptOption();
    option.setOptionKey("A");
    option.setIsCorrectSnapshot(1);
    when(attemptOptionMapper.selectList(any())).thenReturn(List.of(option));

    ExamCandidate candidate = new ExamCandidate();
    candidate.setExamCandidateId(20L);
    candidate.setTrainingParticipantId(9L);
    when(examCandidateMapper.selectById(20L)).thenReturn(candidate);
    Exam exam = new Exam();
    exam.setPlanContentId(30L);
    AssessmentExecution execution = new AssessmentExecution();
    execution.setExamId(1L);
    when(assessmentExecutionService.requireById(11L)).thenReturn(execution);
    when(examMapper.selectById(any())).thenReturn(exam);

    var vo = service.submit(40L, new SubmitAttemptRequest());

    assertThat(vo.getAttemptStatus()).isEqualTo("PUBLISHED");
    assertThat(vo.getScorePublishStatus()).isEqualTo("PUBLISHED");
    assertThat(candidate.getCandidateStatus()).isEqualTo("PASSED");
    verify(eventPublisher).publishEvent(any(TrainingParticipantContentFactChangedEvent.class));
  }

  @Test
  @DisplayName("submit：独立题库练习混合题自动核算客观题，简答题只记录")
  void submit_standaloneBankPracticeAutoScoresObjectiveWithoutManualReview() {
    ExamAttempt attempt = answeringAttempt();
    attempt.setSourceType("STANDALONE_PRACTICE");
    attempt.setAssessmentKind("BANK_PRACTICE");
    attempt.setTotalScoreSnapshot(null);
    when(examAttemptMapper.selectById(40L)).thenReturn(attempt);

    ExamAttemptQuestion objective = attemptQuestion(60L, "SINGLE", "AUTO");
    objective.setScore(BigDecimal.ONE);
    ExamAttemptQuestion essay = attemptQuestion(61L, "ESSAY", "RECORD_ONLY");
    when(attemptQuestionMapper.selectList(any())).thenReturn(List.of(objective, essay));
    when(attemptQuestionMapper.selectCount(any())).thenReturn(2L);
    when(examAnswerMapper.selectCount(any())).thenReturn(2L);
    ExamAnswer objectiveAnswer = answered(70L, 60L, "A");
    ExamAnswer essayAnswer = answered(71L, 61L, "我的理解");
    when(examAnswerMapper.selectOne(any())).thenReturn(objectiveAnswer, essayAnswer);
    when(examAnswerMapper.selectList(any())).thenReturn(List.of(objectiveAnswer, essayAnswer));
    ExamAttemptOption option = new ExamAttemptOption();
    option.setOptionKey("A");
    option.setIsCorrectSnapshot(1);
    when(attemptOptionMapper.selectList(any())).thenReturn(List.of(option));
    ExamCandidate candidate = new ExamCandidate();
    candidate.setExamCandidateId(20L);
    when(examCandidateMapper.selectById(20L)).thenReturn(candidate);

    var result = service.submit(40L, new SubmitAttemptRequest());

    assertThat(result.getAttemptStatus()).isEqualTo("RESULT_EFFECTIVE");
    assertThat(attempt.getFinalScore()).isEqualByComparingTo("1");
    assertThat(attempt.getObjectiveScore()).isEqualByComparingTo("1");
    assertThat(attempt.getIsPassed()).isNull();
    assertThat(attempt.getEvaluatedCount()).isEqualTo(1);
    assertThat(attempt.getCorrectCount()).isEqualTo(1);
    assertThat(attempt.getWrongCount()).isZero();
    assertThat(attempt.getUnassessedCount()).isEqualTo(1);
    assertThat(attempt.getAccuracyRate()).isEqualByComparingTo("100.00");
    assertThat(objectiveAnswer.getEvaluationResult()).isEqualTo("AUTO_CORRECT");
    assertThat(objectiveAnswer.getAutoScore()).isEqualByComparingTo("1");
    assertThat(objectiveAnswer.getFinalScore()).isEqualByComparingTo("1");
    assertThat(essayAnswer.getEvaluationResult()).isEqualTo("NOT_EVALUATED");
    assertThat(essayAnswer.getIsCorrect()).isNull();
    assertThat(candidate.getCandidateStatus()).isEqualTo("COMPLETED");
    verify(reviewRecordMapper, never()).insert(any(ExamReviewRecord.class));
    verify(courseLearningService, never()).applyEffectiveAssessmentResult(any());
  }

  @Test
  @DisplayName("submit：新版全简答练习完成时正确率为空")
  void submit_allRecordOnlyPracticeHasNullAccuracy() {
    ExamAttempt attempt = answeringAttempt();
    attempt.setSourceType("TRAINING_PRACTICE");
    attempt.setTotalScoreSnapshot(null);
    when(examAttemptMapper.selectById(40L)).thenReturn(attempt);
    ExamAttemptQuestion essay = attemptQuestion(61L, "ESSAY", "RECORD_ONLY");
    when(attemptQuestionMapper.selectList(any())).thenReturn(List.of(essay));
    when(attemptQuestionMapper.selectCount(any())).thenReturn(1L);
    when(examAnswerMapper.selectCount(any())).thenReturn(1L);
    ExamAnswer answer = answered(71L, 61L, "练习作答");
    when(examAnswerMapper.selectList(any())).thenReturn(List.of(answer));
    ExamCandidate candidate = new ExamCandidate();
    candidate.setExamCandidateId(20L);
    when(examCandidateMapper.selectById(20L)).thenReturn(candidate);

    service.submit(40L, new SubmitAttemptRequest());

    assertThat(attempt.getEvaluatedCount()).isZero();
    assertThat(attempt.getUnassessedCount()).isEqualTo(1);
    assertThat(attempt.getAccuracyRate()).isNull();
    assertThat(answer.getEvaluationResult()).isEqualTo("NOT_EVALUATED");
  }

  @Test
  @DisplayName("submit：新版培训练习未全部作答时保持作答中")
  void submit_unansweredTrainingPracticeRejected() {
    ExamAttempt attempt = answeringAttempt();
    attempt.setSourceType("TRAINING_PRACTICE");
    attempt.setTotalScoreSnapshot(null);
    when(examAttemptMapper.selectById(40L)).thenReturn(attempt);
    when(attemptQuestionMapper.selectList(any())).thenReturn(List.of(attemptQuestion(60L, "SINGLE", "AUTO")));
    when(attemptQuestionMapper.selectCount(any())).thenReturn(2L);
    when(examAnswerMapper.selectCount(any())).thenReturn(1L);

    assertThatThrownBy(() -> service.submit(40L, new SubmitAttemptRequest()))
      .isInstanceOfSatisfying(BusinessException.class, ex -> assertThat(ex.getCode()).isEqualTo(7064));
    assertThat(attempt.getAttemptStatus()).isEqualTo("ANSWERING");
    verify(examAnswerMapper, never()).updateById(any(ExamAnswer.class));
  }

  @Test
  @DisplayName("submit：培训考生交卷发布投影刷新事件")
  void submit_trainingCandidate_publishesProjectionEvent() {
    ExamAttempt attempt = answeringAttempt();
    when(examAttemptMapper.selectById(40L)).thenReturn(attempt);

    ExamAttemptQuestion aq = new ExamAttemptQuestion();
    aq.setAttemptQuestionId(60L);
    aq.setExamAttemptId(40L);
    aq.setQuestionType("SINGLE");
    aq.setGradingMode("AUTO");
    aq.setScore(new BigDecimal("10"));
    aq.setCorrectAnswerSnapshotJson("{\"correctOptions\":[\"A\"]}");
    when(attemptQuestionMapper.selectList(any())).thenReturn(List.of(aq));
    when(attemptQuestionMapper.selectById(60L)).thenReturn(aq);

    ExamAnswer answer = new ExamAnswer();
    answer.setExamAnswerId(70L);
    answer.setExamAttemptId(40L);
    answer.setAttemptQuestionId(60L);
    answer.setAnswerText("A");
    answer.setIsAnswered(1);
    answer.setFinalScore(BigDecimal.ZERO);
    when(examAnswerMapper.selectOne(any())).thenReturn(answer);
    when(examAnswerMapper.selectList(any())).thenReturn(List.of(answer));
    when(examAnswerItemMapper.selectList(any())).thenReturn(List.of());

    ExamAttemptOption opt = new ExamAttemptOption();
    opt.setOptionKey("A");
    opt.setIsCorrectSnapshot(1);
    when(attemptOptionMapper.selectList(any())).thenReturn(List.of(opt));
    ExamCandidate candidate = new ExamCandidate();
    candidate.setExamCandidateId(20L);
    candidate.setTrainingParticipantId(2L);
    candidate.setSourceBusinessId(11L);
    when(examCandidateMapper.selectById(20L)).thenReturn(candidate);
    Exam exam = publishedExam();
    exam.setPlanContentId(11L);
    when(examMapper.selectById(1L)).thenReturn(exam);

    service.submit(40L, new SubmitAttemptRequest());

    assertTrainingProjectionEvent(2L, 11L);
  }

  @Test
  @DisplayName("reviewAnswer：只追加批阅记录")
  void review_appendsRecord() {
    ExamAnswer answer = new ExamAnswer();
    answer.setExamAnswerId(70L);
    answer.setExamAttemptId(40L);
    answer.setFinalScore(BigDecimal.ZERO);
    answer.setGradingStatus("PENDING_MANUAL");
    when(examAnswerMapper.selectById(70L)).thenReturn(answer);

    ExamAttempt attempt = answeringAttempt();
    attempt.setAttemptStatus("PENDING_REVIEW");
    when(examAttemptMapper.selectById(40L)).thenReturn(attempt);
    when(examAnswerMapper.selectList(any())).thenReturn(List.of(answer));
    when(examAnswerMapper.selectCount(any())).thenReturn(0L);
    when(attemptQuestionMapper.selectById(any())).thenReturn(new ExamAttemptQuestion());
    ExamCandidate candidate = new ExamCandidate();
    candidate.setExamCandidateId(20L);
    when(examCandidateMapper.selectById(20L)).thenReturn(candidate);

    ReviewAnswerRequest req = new ReviewAnswerRequest();
    req.setAfterScore(new BigDecimal("8"));
    when(examReviewerMapper.selectCount(any())).thenReturn(1L);
    try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
      stp.when(StpUtil::getLoginIdAsLong).thenReturn(8L);
      service.reviewAnswer(70L, req);
    }
    verify(reviewRecordMapper).insert(any(ExamReviewRecord.class));
    verify(reviewRecordMapper).insert(argThat((ExamReviewRecord record) ->
                                                record.getReviewerUserId().equals(8L)));
    assertThat(answer.getGradingStatus()).isEqualTo("MANUAL_GRADED");
    assertThat(candidate.getCandidateStatus()).isEqualTo("PENDING_PUBLISH");
    assertThat(candidate.getFinalAttemptId()).isEqualTo(40L);
    verify(examCandidateMapper).updateById(candidate);
  }

  @Test
  @DisplayName("submit：独立考试交卷只更新考试执行事实")
  void submit_updatesExamFactsOnly() {
    ExamAttempt attempt = answeringAttempt();
    when(examAttemptMapper.selectById(40L)).thenReturn(attempt);
    when(attemptQuestionMapper.selectList(any())).thenReturn(List.of());
    when(examAnswerMapper.selectList(any())).thenReturn(List.of());
    when(examAnswerMapper.selectCount(any())).thenReturn(0L);
    ExamCandidate candidate = new ExamCandidate();
    candidate.setExamCandidateId(20L);
    candidate.setOrganizationId(1L);
    candidate.setAssessmentExecutionId(11L);
    candidate.setUserId(7L);
    candidate.setCourseEnrollmentId(1000L);
    candidate.setCourseOfferingId(100L);
    candidate.setCourseItemId(101L);
    when(examCandidateMapper.selectById(20L)).thenReturn(candidate);

    var vo = service.submit(40L, new SubmitAttemptRequest());

    assertThat(vo.getAttemptStatus()).isEqualTo("PENDING_PUBLISH");
    verify(examCandidateMapper).updateById(candidate);
  }

  @Test
  @DisplayName("submit：课程题库练习未答完全部题目时拒绝完成一轮")
  void submit_courseBankPractice_requiresAllQuestionsAnswered() {
    ExamAttempt attempt = answeringAttempt();
    attempt.setSourceType("COURSE_BANK_PRACTICE");
    attempt.setAssessmentKind("BANK_PRACTICE");
    when(examAttemptMapper.selectById(40L)).thenReturn(attempt);
    when(attemptQuestionMapper.selectCount(any())).thenReturn(2L);
    when(examAnswerMapper.selectCount(any())).thenReturn(1L);

    assertThatThrownBy(() -> service.submit(40L, new SubmitAttemptRequest()))
      .isInstanceOfSatisfying(BusinessException.class, error -> {
        assertThat(error.getCode()).isEqualTo(7064);
        assertThat(error.getMessage()).isEqualTo("题库练习需完成全部题目后提交");
      });
    verify(examAttemptMapper, never()).updateById(any(ExamAttempt.class));
  }

  @Test
  @DisplayName("submit：课程题库练习答完全部题目后立即生效课程完成事实")
  void submit_courseBankPractice_projectsEffectiveCourseResult() {
    ExamAttempt attempt = answeringAttempt();
    attempt.setAssessmentExecutionId(22L);
    attempt.setSourceType("COURSE_BANK_PRACTICE");
    attempt.setAssessmentKind("BANK_PRACTICE");
    when(examAttemptMapper.selectById(40L)).thenReturn(attempt);
    when(attemptQuestionMapper.selectCount(any())).thenReturn(1L);
    when(examAnswerMapper.selectCount(any())).thenReturn(1L, 0L);
    when(attemptQuestionMapper.selectList(any())).thenReturn(List.of());
    when(examAnswerMapper.selectList(any())).thenReturn(List.of());
    ExamCandidate candidate = new ExamCandidate();
    candidate.setExamCandidateId(20L);
    candidate.setOrganizationId(1L);
    candidate.setAssessmentExecutionId(22L);
    candidate.setUserId(7L);
    candidate.setCourseEnrollmentId(1000L);
    candidate.setCourseOfferingId(100L);
    candidate.setCourseItemId(101L);
    when(examCandidateMapper.selectById(20L)).thenReturn(candidate);

    var result = service.submit(40L, new SubmitAttemptRequest());

    assertThat(result.getAttemptStatus()).isEqualTo("RESULT_EFFECTIVE");
    assertThat(attempt.getResultEffectiveTime()).isNotNull();
    verify(courseLearningService).applyEffectiveAssessmentResult(
      argThat((CourseAssessmentResultEffective fact) ->
                fact.assessmentExecutionId().equals(22L)
                  && fact.examAttemptId().equals(40L)
                  && fact.courseEnrollmentId().equals(1000L)
                  && "COURSE_BANK_PRACTICE".equals(fact.sourceType())));
  }

  @Test
  @DisplayName("submit：课程测验含主观题时交卷即投影待批阅进度，不提前产生成绩事实")
  void submit_courseQuizWithManualAnswers_projectsPendingProgress() {
    ExamAttempt attempt = answeringAttempt();
    attempt.setAssessmentExecutionId(22L);
    attempt.setSourceType("COURSE_QUIZ");
    attempt.setAssessmentKind("QUIZ");
    when(examAttemptMapper.selectById(40L)).thenReturn(attempt);
    when(attemptQuestionMapper.selectList(any())).thenReturn(List.of());
    when(examAnswerMapper.selectList(any())).thenReturn(List.of());
    // 仍有 1 道主观题待批阅：只能交卷，不能自动生效。
    when(examAnswerMapper.selectCount(any())).thenReturn(1L);
    ExamCandidate candidate = new ExamCandidate();
    candidate.setExamCandidateId(20L);
    candidate.setOrganizationId(1L);
    candidate.setAssessmentExecutionId(22L);
    candidate.setUserId(7L);
    candidate.setCourseEnrollmentId(1000L);
    candidate.setCourseOfferingId(100L);
    candidate.setCourseItemId(101L);
    when(examCandidateMapper.selectById(20L)).thenReturn(candidate);

    var result = service.submit(40L, new SubmitAttemptRequest());

    assertThat(result.getAttemptStatus()).isEqualTo("PENDING_REVIEW");
    assertThat(attempt.getResultEffectiveTime()).isNull();
    verify(courseLearningService, never()).applyEffectiveAssessmentResult(any());
    verify(courseLearningService).applySubmittedAssessmentResult(
      argThat((CourseAssessmentSubmitted fact) ->
                fact.assessmentExecutionId().equals(22L)
                  && fact.examAttemptId().equals(40L)
                  && fact.courseEnrollmentId().equals(1000L)
                  && fact.courseOfferingId().equals(100L)
                  && fact.courseItemId().equals(101L)
                  && "COURSE_QUIZ".equals(fact.sourceType())
                  && fact.submittedTime() != null));
  }

  @Test
  @DisplayName("submit：纯客观题课程测验判分后自动生效且不等待人工发布")
  void submit_objectiveCourseQuiz_becomesEffectiveAutomatically() {
    ExamAttempt attempt = answeringAttempt();
    attempt.setAssessmentExecutionId(22L);
    attempt.setSourceType("COURSE_QUIZ");
    attempt.setAssessmentKind("QUIZ");
    when(examAttemptMapper.selectById(40L)).thenReturn(attempt);
    when(attemptQuestionMapper.selectList(any())).thenReturn(List.of());
    when(examAnswerMapper.selectList(any())).thenReturn(List.of());
    when(examAnswerMapper.selectCount(any())).thenReturn(0L);
    ExamCandidate candidate = new ExamCandidate();
    candidate.setExamCandidateId(20L);
    candidate.setOrganizationId(1L);
    candidate.setAssessmentExecutionId(22L);
    candidate.setUserId(7L);
    candidate.setCourseEnrollmentId(1000L);
    candidate.setCourseOfferingId(100L);
    candidate.setCourseItemId(101L);
    when(examCandidateMapper.selectById(20L)).thenReturn(candidate);

    var result = service.submit(40L, new SubmitAttemptRequest());

    assertThat(result.getAttemptStatus()).isEqualTo("RESULT_EFFECTIVE");
    assertThat(attempt.getScorePublishedTime()).isNull();
    assertThat(attempt.getResultEffectiveTime()).isNotNull();
    verify(scorePublishLogMapper, never()).insert(any(ExamScorePublishLog.class));
    verify(courseLearningService).applyEffectiveAssessmentResult(any(CourseAssessmentResultEffective.class));
  }

  @Test
  @DisplayName("reviewCourseAnswer：最后一道主观题批阅后课程测验成绩自动生效")
  void reviewCourseAnswer_lastManualAnswer_becomesEffectiveAutomatically() {
    ExamAnswer answer = new ExamAnswer();
    answer.setExamAnswerId(70L);
    answer.setExamAttemptId(40L);
    answer.setFinalScore(BigDecimal.ZERO);
    answer.setGradingStatus("PENDING_MANUAL");
    when(examAnswerMapper.selectById(70L)).thenReturn(answer);
    when(examAnswerMapper.selectList(any())).thenReturn(List.of(answer));
    when(examAnswerMapper.selectCount(any())).thenReturn(0L);
    when(attemptQuestionMapper.selectById(any())).thenReturn(new ExamAttemptQuestion());
    ExamAttempt attempt = answeringAttempt();
    attempt.setAssessmentExecutionId(22L);
    attempt.setSourceType("COURSE_QUIZ");
    attempt.setAssessmentKind("QUIZ");
    attempt.setAttemptStatus("PENDING_REVIEW");
    when(examAttemptMapper.selectById(40L)).thenReturn(attempt);
    AssessmentExecution execution = new AssessmentExecution();
    execution.setAssessmentExecutionId(22L);
    execution.setSourceType("COURSE_QUIZ");
    execution.setCourseOfferingId(100L);
    execution.setCourseItemId(101L);
    when(assessmentExecutionService.requireById(22L)).thenReturn(execution);
    when(examReviewerMapper.selectCount(any())).thenReturn(1L);
    ExamCandidate candidate = new ExamCandidate();
    candidate.setExamCandidateId(20L);
    candidate.setOrganizationId(1L);
    candidate.setAssessmentExecutionId(22L);
    candidate.setUserId(7L);
    candidate.setCourseEnrollmentId(1000L);
    candidate.setCourseOfferingId(100L);
    candidate.setCourseItemId(101L);
    when(examCandidateMapper.selectById(20L)).thenReturn(candidate);
    ReviewAnswerRequest request = new ReviewAnswerRequest();
    request.setAfterScore(new BigDecimal("8"));

    try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
      stp.when(StpUtil::getLoginIdAsLong).thenReturn(8L);
      service.reviewCourseAnswer(100L, 101L, 70L, request);
    }

    assertThat(attempt.getAttemptStatus()).isEqualTo("RESULT_EFFECTIVE");
    assertThat(attempt.getResultEffectiveTime()).isNotNull();
    verify(courseLearningService).applyEffectiveAssessmentResult(any(CourseAssessmentResultEffective.class));
  }

  @Test
  @DisplayName("reviewAnswer：正式成绩已发布后拒绝改分且不写入批阅记录")
  void review_publishedScore_rejectsWithoutWriting() {
    ExamAnswer answer = new ExamAnswer();
    answer.setExamAnswerId(70L);
    answer.setExamAttemptId(40L);
    answer.setFinalScore(BigDecimal.ZERO);
    answer.setGradingStatus("MANUAL_GRADED");
    when(examAnswerMapper.selectById(70L)).thenReturn(answer);

    ExamAttempt attempt = answeringAttempt();
    attempt.setAttemptStatus("PUBLISHED");
    attempt.setScorePublishedTime(LocalDateTime.now());
    when(examAttemptMapper.selectById(40L)).thenReturn(attempt);

    ReviewAnswerRequest request = new ReviewAnswerRequest();
    request.setAfterScore(new BigDecimal("8"));

    assertThatThrownBy(() -> service.reviewAnswer(70L, request))
      .isInstanceOfSatisfying(BusinessException.class, error -> {
        assertThat(error.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(error.getCode()).isEqualTo(7032);
        assertThat(error).hasMessage("正式成绩已发布，不可批阅或改分");
      });
    verifyNoInteractions(reviewRecordMapper);
    verify(examAnswerMapper, never()).updateById(any(ExamAnswer.class));
  }

  @Test
  @DisplayName("reviewAnswer：未分配到执行对象的当前教师不得批阅")
  void reviewAnswer_rejectsUnassignedCurrentReviewer() {
    ExamAnswer answer = new ExamAnswer();
    answer.setExamAnswerId(70L);
    answer.setExamAttemptId(40L);
    answer.setFinalScore(BigDecimal.ZERO);
    answer.setGradingStatus("PENDING_MANUAL");
    when(examAnswerMapper.selectById(70L)).thenReturn(answer);
    ExamAttempt attempt = answeringAttempt();
    attempt.setAttemptStatus("PENDING_REVIEW");
    when(examAttemptMapper.selectById(40L)).thenReturn(attempt);
    when(examReviewerMapper.selectCount(any())).thenReturn(0L);
    ReviewAnswerRequest request = new ReviewAnswerRequest();
    request.setAfterScore(new BigDecimal("8"));

    try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
      stp.when(StpUtil::getLoginIdAsLong).thenReturn(8L);
      assertThatThrownBy(() -> service.reviewAnswer(70L, request))
        .isInstanceOfSatisfying(BusinessException.class,
                                error -> assertThat(error.getCode()).isEqualTo(7033));
    }
    verifyNoInteractions(reviewRecordMapper);
  }

  @Test
  @DisplayName("get：返回答案最近一次批阅意见")
  void get_returnsLatestReviewComment() {
    ExamAttempt attempt = answeringAttempt();
    when(examAttemptMapper.selectById(40L)).thenReturn(attempt);
    ExamAttemptQuestion question = new ExamAttemptQuestion();
    question.setAttemptQuestionId(60L);
    question.setExamAttemptId(40L);
    question.setSortOrder(1);
    when(attemptQuestionMapper.selectList(any())).thenReturn(List.of(question));
    when(attemptOptionMapper.selectList(any())).thenReturn(List.of());
    ExamAnswer answer = new ExamAnswer();
    answer.setExamAnswerId(70L);
    answer.setExamAttemptId(40L);
    answer.setAttemptQuestionId(60L);
    answer.setAnswerText("要点完整");
    when(examAnswerMapper.selectList(any())).thenReturn(List.of(answer));
    ExamReviewRecord latest = new ExamReviewRecord();
    latest.setExamAnswerId(70L);
    latest.setReviewComment("已覆盖全部要点");
    when(reviewRecordMapper.selectList(any())).thenReturn(List.of(latest));

    var vo = service.get(40L);

    assertThat(vo.getQuestions().getFirst().getAnswer().getReviewComment()).isEqualTo("已覆盖全部要点");
  }

  @Test
  @DisplayName("publishScore：写入发布日志并更新考生")
  void publishScore_updatesCandidate() {
    ExamAttempt attempt = answeringAttempt();
    attempt.setAttemptStatus("PENDING_PUBLISH");
    attempt.setFinalScore(new BigDecimal("10"));
    attempt.setObjectiveScore(new BigDecimal("10"));
    attempt.setSubjectiveScore(BigDecimal.ZERO);
    when(examAttemptMapper.selectById(40L)).thenReturn(attempt);
    when(examAnswerMapper.selectCount(any())).thenReturn(0L);

    ExamAnswer graded = new ExamAnswer();
    graded.setExamAnswerId(70L);
    graded.setExamAttemptId(40L);
    graded.setAttemptQuestionId(60L);
    graded.setFinalScore(new BigDecimal("10"));
    graded.setIsCorrect(1);
    graded.setIsAnswered(1);
    graded.setGradingStatus("AUTO_GRADED");
    when(examAnswerMapper.selectList(any())).thenReturn(List.of(graded));
    ExamAttemptQuestion aq = new ExamAttemptQuestion();
    aq.setAttemptQuestionId(60L);
    aq.setQuestionType("SINGLE");
    aq.setGradingMode("AUTO");
    when(attemptQuestionMapper.selectById(60L)).thenReturn(aq);

    ExamCandidate candidate = new ExamCandidate();
    candidate.setExamCandidateId(20L);
    candidate.setAssessmentExecutionId(11L);
    candidate.setOrganizationId(1L);
    candidate.setUserId(7L);
    candidate.setCourseEnrollmentId(1000L);
    candidate.setCourseOfferingId(100L);
    candidate.setCourseItemId(101L);
    when(examCandidateMapper.selectById(20L)).thenReturn(candidate);
    when(examPolicyMapper.selectOne(any())).thenReturn(null);

    var vo = service.publishScore(40L, 99L);
    assertThat(vo.getAttemptStatus()).isEqualTo("PUBLISHED");
    assertThat(vo.getIsPassed()).isEqualTo(1);
    assertThat(vo.getScorePublishStatus()).isEqualTo("PUBLISHED");
    assertThat(vo.getScorePublishedTime()).isNotNull();
    verify(scorePublishLogMapper).insert(any(ExamScorePublishLog.class));
    assertThat(candidate.getCandidateStatus()).isEqualTo("PASSED");
    verify(eventPublisher, never()).publishEvent(any(Object.class));
  }

  @Test
  @DisplayName("publishScore：独立题库练习已自动生效时幂等返回")
  void publishScore_standaloneBankPracticeAlreadyEffective() {
    ExamAttempt attempt = answeringAttempt();
    attempt.setSourceType("STANDALONE_PRACTICE");
    attempt.setAssessmentKind("BANK_PRACTICE");
    attempt.setAttemptStatus("RESULT_EFFECTIVE");
    attempt.setPassScoreSnapshot(null);
    when(examAttemptMapper.selectById(40L)).thenReturn(attempt);

    ExamCandidate candidate = new ExamCandidate();
    candidate.setExamCandidateId(20L);
    candidate.setAssessmentExecutionId(11L);
    when(examCandidateMapper.selectById(20L)).thenReturn(candidate);
    when(examPolicyMapper.selectOne(any())).thenReturn(null);

    var vo = service.publishScore(40L, 99L);

    assertThat(vo.getAttemptStatus()).isEqualTo("RESULT_EFFECTIVE");
    assertThat(vo.getIsPassed()).isNull();
    assertThat(candidate.getCandidateStatus()).isNull();
    verifyNoInteractions(scorePublishLogMapper);
  }

  @Test
  @DisplayName("publishScore：培训考生发布成绩时刷新投影")
  void publishScore_trainingCandidate_publishesProjectionEvent() {
    ExamAttempt attempt = answeringAttempt();
    attempt.setAttemptStatus("PENDING_PUBLISH");
    attempt.setFinalScore(new BigDecimal("10"));
    attempt.setObjectiveScore(new BigDecimal("10"));
    attempt.setSubjectiveScore(BigDecimal.ZERO);
    when(examAttemptMapper.selectById(40L)).thenReturn(attempt);
    when(examAnswerMapper.selectCount(any())).thenReturn(0L);

    ExamAnswer graded = new ExamAnswer();
    graded.setExamAnswerId(70L);
    graded.setExamAttemptId(40L);
    graded.setAttemptQuestionId(60L);
    graded.setFinalScore(new BigDecimal("10"));
    graded.setIsCorrect(1);
    graded.setIsAnswered(1);
    graded.setGradingStatus("AUTO_GRADED");
    when(examAnswerMapper.selectList(any())).thenReturn(List.of(graded));
    ExamAttemptQuestion aq = new ExamAttemptQuestion();
    aq.setAttemptQuestionId(60L);
    aq.setQuestionType("SINGLE");
    aq.setGradingMode("AUTO");
    when(attemptQuestionMapper.selectById(60L)).thenReturn(aq);

    ExamCandidate candidate = new ExamCandidate();
    candidate.setExamCandidateId(20L);
    candidate.setAssessmentExecutionId(11L);
    candidate.setTrainingParticipantId(2L);
    candidate.setSourceBusinessId(11L);
    when(examCandidateMapper.selectById(20L)).thenReturn(candidate);
    when(examPolicyMapper.selectOne(any())).thenReturn(null);
    Exam exam = publishedExam();
    exam.setPlanContentId(11L);
    when(examMapper.selectById(1L)).thenReturn(exam);

    service.publishScore(40L, 99L);

    assertTrainingProjectionEvent(2L, 11L);
  }

  @Test
  @DisplayName("publishScore：独立考试考生通过后按 exam_id 自动签发证书")
  void publishScore_standaloneCandidate_issuesExamCertificate() {
    ExamAttempt attempt = answeringAttempt();
    attempt.setAttemptStatus("PENDING_PUBLISH");
    attempt.setFinalScore(new BigDecimal("10"));
    attempt.setObjectiveScore(new BigDecimal("10"));
    attempt.setSubjectiveScore(BigDecimal.ZERO);
    when(examAttemptMapper.selectById(40L)).thenReturn(attempt);
    when(examAnswerMapper.selectCount(any())).thenReturn(0L);
    when(examAnswerMapper.selectList(any())).thenReturn(List.of(gradedAnswer()));
    when(attemptQuestionMapper.selectById(60L)).thenReturn(autoQuestion());

    ExamCandidate candidate = new ExamCandidate();
    candidate.setExamCandidateId(20L);
    candidate.setAssessmentExecutionId(11L);
    candidate.setUserId(7L);
    candidate.setAssignmentSource("ASSIGN");
    when(examCandidateMapper.selectById(20L)).thenReturn(candidate);
    when(examPolicyMapper.selectOne(any())).thenReturn(null);
    when(examMapper.selectById(1L)).thenReturn(publishedExam());

    service.publishScore(40L, 99L);

    verify(certificateAutoIssueService).issueForSource("EXAM", 1L, 7L, 20L);
  }

  @Test
  @DisplayName("publishScore：培训派生考试考生通过后不触发独立理论考试证书")
  void publishScore_trainingDerivedCandidate_skipsExamCertificate() {
    ExamAttempt attempt = answeringAttempt();
    attempt.setAttemptStatus("PENDING_PUBLISH");
    attempt.setFinalScore(new BigDecimal("10"));
    attempt.setObjectiveScore(new BigDecimal("10"));
    attempt.setSubjectiveScore(BigDecimal.ZERO);
    when(examAttemptMapper.selectById(40L)).thenReturn(attempt);
    when(examAnswerMapper.selectCount(any())).thenReturn(0L);
    when(examAnswerMapper.selectList(any())).thenReturn(List.of(gradedAnswer()));
    when(attemptQuestionMapper.selectById(60L)).thenReturn(autoQuestion());

    ExamCandidate candidate = new ExamCandidate();
    candidate.setExamCandidateId(20L);
    candidate.setAssessmentExecutionId(11L);
    candidate.setUserId(7L);
    candidate.setTrainingParticipantId(2L);
    candidate.setSourceBusinessId(11L);
    candidate.setAssignmentSource("TRAINING");
    when(examCandidateMapper.selectById(20L)).thenReturn(candidate);
    when(examPolicyMapper.selectOne(any())).thenReturn(null);
    Exam exam = publishedExam();
    exam.setPlanContentId(11L);
    when(examMapper.selectById(1L)).thenReturn(exam);

    service.publishScore(40L, 99L);

    verify(certificateAutoIssueService, never()).issueForSource(any(), any(), any(), any());
  }

  @Test
  @DisplayName("publishScore：课程考核拒绝人工发布且不修改作答")
  void publishScore_courseAssessment_rejectsBeforeMutation() {
    ExamAttempt attempt = answeringAttempt();
    attempt.setAssessmentExecutionId(22L);
    attempt.setAttemptStatus("PENDING_PUBLISH");
    when(examAttemptMapper.selectById(40L)).thenReturn(attempt);
    AssessmentExecution execution = new AssessmentExecution();
    execution.setAssessmentExecutionId(22L);
    execution.setSourceType("COURSE_QUIZ");
    when(assessmentExecutionService.requireById(22L)).thenReturn(execution);

    assertThatThrownBy(() -> service.publishScore(40L, 99L))
      .isInstanceOfSatisfying(BusinessException.class, error -> assertThat(error.getCode()).isEqualTo(7042));
    assertThat(attempt.getAttemptStatus()).isEqualTo("PENDING_PUBLISH");
    verify(examAttemptMapper, never()).updateById(any(ExamAttempt.class));
    verifyNoInteractions(scorePublishLogMapper);
  }

  @Test
  @DisplayName("retake：未启用补考返回 7051 且不创建尝试")
  void retake_disabled_throws7051() {
    ExamPolicy policy = enabledRetakePolicy();
    policy.setIsRetakeEnabled(0);
    stubRetakeLookups(failedCandidate(), policy, publishedExam());

    assertRetakeCode(7051, "未启用补考");
    verify(examAttemptMapper, never()).insert(any(ExamAttempt.class));
  }

  @Test
  @DisplayName("retake：已及格不在 FAILED 范围，返回 7055")
  void retake_passed_throws7055() {
    ExamCandidate candidate = failedCandidate();
    candidate.setCandidateStatus("PASSED");
    stubRetakeLookups(candidate, enabledRetakePolicy(), publishedExam());

    assertRetakeCode(7055, "当前考生不在补考范围");
    verify(examAttemptMapper, never()).insert(any(ExamAttempt.class));
  }

  @Test
  @DisplayName("retake：不及格但成绩未正式发布，返回 7055")
  void retake_failedWithoutPublish_throws7055() {
    ExamCandidate candidate = failedCandidate();
    candidate.setResultPublishedTime(null);
    stubRetakeLookups(candidate, enabledRetakePolicy(), publishedExam());

    assertRetakeCode(7055, "当前考生不在补考范围");
    verify(examAttemptMapper, never()).insert(any(ExamAttempt.class));
  }

  @Test
  @DisplayName("retake：范围 ABSENT 时拒绝已发布不及格考生")
  void retake_absentScope_rejectsFailed() {
    ExamPolicy policy = enabledRetakePolicy();
    policy.setRetakeScope("ABSENT");
    stubRetakeLookups(failedCandidate(), policy, publishedExam());

    assertRetakeCode(7055, "当前考生不在补考范围");
  }

  @Test
  @DisplayName("retake：早于补考开始时间返回 7054")
  void retake_beforeWindow_throws7054() {
    ExamPolicy policy = enabledRetakePolicy();
    policy.setRetakeStartTime(LocalDateTime.now().plusHours(1));
    stubRetakeLookups(failedCandidate(), policy, publishedExam());

    assertRetakeCode(7054, "不在补考开放时间内");
    verify(examAttemptMapper, never()).insert(any(ExamAttempt.class));
  }

  @Test
  @DisplayName("retake：晚于补考结束时间返回 7054")
  void retake_afterWindow_throws7054() {
    ExamPolicy policy = enabledRetakePolicy();
    policy.setRetakeEndTime(LocalDateTime.now().minusHours(1));
    stubRetakeLookups(failedCandidate(), policy, publishedExam());

    assertRetakeCode(7054, "不在补考开放时间内");
  }

  @Test
  @DisplayName("retake：已有补考次数达到上限返回 7052，首考次数不单独超限")
  void retake_maxCountByRetakeAttempts_throws7052() {
    ExamCandidate candidate = failedCandidate();
    candidate.setAttemptCount(1);
    ExamPolicy policy = enabledRetakePolicy();
    policy.setRetakeMaxCount(1);
    stubRetakeLookups(candidate, policy, publishedExam());
    when(examAttemptMapper.selectCount(any())).thenReturn(1L);

    assertRetakeCode(7052, "已超过补考次数");
    verify(examAttemptMapper, never()).insert(any(ExamAttempt.class));
  }

  @Test
  @DisplayName("retake：考试已取消返回 7056")
  void retake_cancelledExam_throws7056() {
    Exam exam = publishedExam();
    exam.setPublishStatus("CANCELLED");
    stubRetakeLookups(failedCandidate(), enabledRetakePolicy(), exam);

    assertRetakeCode(7056, "考试状态不允许补考");
  }

  @Test
  @DisplayName("retake：ENDED 且窗口内的已发布不及格考生可开补考")
  void retake_endedExam_createsRetakeAttempt() {
    Exam exam = publishedExam();
    exam.setPublishStatus("ENDED");
    stubRetakeLookups(failedCandidate(), enabledRetakePolicy(), exam);
    stubStartAfterPolicy();

    var vo = service.retake(20L, "tok-retake-1");

    assertThat(vo.getExamAttemptId()).isEqualTo(40L);
    assertThat(vo.getAttemptStatus()).isEqualTo("ANSWERING");
    verify(examAttemptMapper).insert(argThat((ExamAttempt attempt) ->
                                               "RETAKE".equals(attempt.getAttemptType())
                                                 && Integer.valueOf(3600).equals(attempt.getDurationSecondsSnapshot())));
  }

  @Test
  @DisplayName("retake：FAILED_OR_ABSENT 允许未参加考生")
  void retake_absentCandidate_whenFailedOrAbsent() {
    ExamCandidate candidate = failedCandidate();
    candidate.setCandidateStatus("ABSENT");
    candidate.setResultPublishedTime(null);
    candidate.setAttemptCount(0);
    ExamPolicy policy = enabledRetakePolicy();
    policy.setRetakeScope("FAILED_OR_ABSENT");
    stubRetakeLookups(candidate, policy, publishedExam());
    stubStartAfterPolicy();

    var vo = service.retake(20L, "tok-retake-1");

    assertThat(vo.getExamAttemptId()).isEqualTo(40L);
    verify(examAttemptMapper).insert(argThat((ExamAttempt attempt) -> "RETAKE".equals(attempt.getAttemptType())));
  }

  @Test
  @DisplayName("retake：进行中补考恢复原尝试，不新增次数")
  void retake_resumesInProgress() {
    ExamAttempt answering = answeringAttempt();
    answering.setAttemptType("RETAKE");
    stubRetakeLookups(failedCandidate(), enabledRetakePolicy(), publishedExam());
    when(examAttemptMapper.selectOne(any())).thenReturn(null, answering);
    when(attemptQuestionMapper.selectList(any())).thenReturn(List.of());

    var vo = service.retake(20L, "tok-retake-1");

    assertThat(vo.getExamAttemptId()).isEqualTo(40L);
    verify(examAttemptMapper, never()).insert(any(ExamAttempt.class));
  }

  @Test
  @DisplayName("get：符合全部规则时返回可开补考资格")
  void get_returnsEligibleRetakeSummary() {
    ExamAttempt attempt = answeringAttempt();
    when(examAttemptMapper.selectById(40L)).thenReturn(attempt);
    when(examCandidateMapper.selectById(20L)).thenReturn(failedCandidate());
    when(examMapper.selectById(1L)).thenReturn(publishedExam());
    when(examPolicyMapper.selectOne(any())).thenReturn(enabledRetakePolicy());
    when(examAttemptMapper.selectCount(any())).thenReturn(0L);
    when(paperVersionMapper.selectById(30L)).thenReturn(new PaperVersion());

    var result = service.get(40L);

    assertThat(result.getRetakeEligibility().getEligible()).isTrue();
    assertThat(result.getRetakeEligibility().getReasons()).isEmpty();
    assertThat(result.getRetakeEligibility().getUsedRetakeCount()).isZero();
    assertThat(result.getRetakeEligibility().getMaxRetakeCount()).isEqualTo(1);
  }

  @Test
  @DisplayName("get：待发布作答返回服务端派生的预计及格结果")
  void get_returnsPredictedPassResultBeforeScorePublish() {
    ExamAttempt attempt = answeringAttempt();
    attempt.setAttemptStatus("PENDING_PUBLISH");
    attempt.setFinalScore(new BigDecimal("8"));
    attempt.setPassScoreSnapshot(new BigDecimal("6"));
    when(examAttemptMapper.selectById(40L)).thenReturn(attempt);
    when(examCandidateMapper.selectById(20L)).thenReturn(failedCandidate());
    when(examMapper.selectById(1L)).thenReturn(publishedExam());
    when(examPolicyMapper.selectOne(any())).thenReturn(enabledRetakePolicy());
    when(examAttemptMapper.selectCount(any())).thenReturn(0L);
    when(paperVersionMapper.selectById(30L)).thenReturn(new PaperVersion());
    when(examAnswerMapper.selectCount(any())).thenReturn(0L);

    var result = service.get(40L);

    assertThat(result.getIsPassed()).isNull();
    assertThat(result.getPredictedIsPassed()).isEqualTo(1);
  }

  @Test
  @DisplayName("get：冻结试卷不可用时返回不可开补考原因")
  void get_returnsRetakeIneligibleWhenFrozenPaperMissing() {
    ExamAttempt attempt = answeringAttempt();
    when(examAttemptMapper.selectById(40L)).thenReturn(attempt);
    when(examCandidateMapper.selectById(20L)).thenReturn(failedCandidate());
    when(examMapper.selectById(1L)).thenReturn(publishedExam());
    when(examPolicyMapper.selectOne(any())).thenReturn(enabledRetakePolicy());
    when(examAttemptMapper.selectCount(any())).thenReturn(0L);
    when(paperVersionMapper.selectById(30L)).thenReturn(null);

    var result = service.get(40L);

    assertThat(result.getRetakeEligibility().getEligible()).isFalse();
    assertThat(result.getRetakeEligibility().getReasons()).contains("当前考试的冻结试卷不可用");
  }

  @Test
  @DisplayName("retake：冻结试卷不可用时不得创建补考作答")
  void retake_missingFrozenPaper_throws7005() {
    stubRetakeLookups(failedCandidate(), enabledRetakePolicy(), publishedExam());
    when(paperVersionMapper.selectById(30L)).thenReturn(null);

    assertThatThrownBy(() -> service.retake(20L, "tok-retake-1"))
      .isInstanceOfSatisfying(BusinessException.class, ex -> {
        assertThat(ex.getCode()).isEqualTo(7005);
        assertThat(ex.getMessage()).isEqualTo("当前考试的冻结试卷不可用");
      });
    verify(examAttemptMapper, never()).insert(any(ExamAttempt.class));
  }

  @Test
  @DisplayName("get：冻结试卷题目版本缺失时返回不可开补考原因")
  void get_returnsRetakeIneligibleWhenFrozenQuestionVersionMissing() {
    ExamAttempt attempt = answeringAttempt();
    PaperVersionQuestion paperQuestion = new PaperVersionQuestion();
    paperQuestion.setQuestionVersionId(50L);
    when(examAttemptMapper.selectById(40L)).thenReturn(attempt);
    when(examCandidateMapper.selectById(20L)).thenReturn(failedCandidate());
    when(examMapper.selectById(1L)).thenReturn(publishedExam());
    when(examPolicyMapper.selectOne(any())).thenReturn(enabledRetakePolicy());
    when(examAttemptMapper.selectCount(any())).thenReturn(0L);
    when(paperVersionMapper.selectById(30L)).thenReturn(new PaperVersion());
    when(paperVersionQuestionMapper.selectList(any())).thenReturn(List.of(paperQuestion));
    when(questionVersionMapper.selectById(50L)).thenReturn(null);

    var result = service.get(40L);

    assertThat(result.getRetakeEligibility().getEligible()).isFalse();
    assertThat(result.getRetakeEligibility().getReasons()).contains("当前考试的冻结试卷题目版本缺失");
  }

  @Test
  @DisplayName("retake：冻结试卷题目版本缺失时不得创建补考作答")
  void retake_missingFrozenQuestionVersion_throws7023() {
    PaperVersionQuestion paperQuestion = new PaperVersionQuestion();
    paperQuestion.setQuestionVersionId(50L);
    stubRetakeLookups(failedCandidate(), enabledRetakePolicy(), publishedExam());
    when(paperVersionMapper.selectById(30L)).thenReturn(new PaperVersion());
    when(paperVersionQuestionMapper.selectList(any())).thenReturn(List.of(paperQuestion));
    when(questionVersionMapper.selectById(50L)).thenReturn(null);

    assertThatThrownBy(() -> service.retake(20L, "tok-retake-1"))
      .isInstanceOfSatisfying(BusinessException.class, ex -> {
        assertThat(ex.getCode()).isEqualTo(7023);
        assertThat(ex.getMessage()).isEqualTo("当前考试的冻结试卷题目版本缺失");
      });
    verify(examAttemptMapper, never()).insert(any(ExamAttempt.class));
  }

  private void assertRetakeCode(int code, String message) {
    assertThatThrownBy(() -> service.retake(20L, "tok-retake-1"))
      .isInstanceOfSatisfying(BusinessException.class, ex -> {
        assertThat(ex.getCode()).isEqualTo(code);
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getMessage()).isEqualTo(message);
      });
  }

  private void stubRetakeLookups(ExamCandidate candidate, ExamPolicy policy, Exam exam) {
    when(examCandidateMapper.selectById(20L)).thenReturn(candidate);
    when(examCandidateMapper.selectOne(any())).thenReturn(candidate);
    when(examPolicyMapper.selectOne(any())).thenReturn(policy);
    when(examMapper.selectById(1L)).thenReturn(exam);
    when(examAttemptMapper.selectOne(any())).thenReturn(null);
    when(examAttemptMapper.selectCount(any())).thenReturn(0L);
  }

  private void stubStartAfterPolicy() {
    PaperVersion pv = new PaperVersion();
    pv.setPaperVersionId(30L);
    pv.setTotalScore(new BigDecimal("10"));
    when(paperVersionMapper.selectById(30L)).thenReturn(pv);
    when(examAttemptMapper.insert(any(ExamAttempt.class))).thenAnswer(inv -> {
      ExamAttempt a = inv.getArgument(0);
      a.setExamAttemptId(40L);
      return 1;
    });
    when(paperVersionQuestionMapper.selectList(any())).thenReturn(List.of());
    when(attemptQuestionMapper.selectList(any())).thenReturn(List.of());
  }

  private ExamPolicy enabledRetakePolicy() {
    ExamPolicy policy = new ExamPolicy();
    policy.setExamId(1L);
    policy.setIsRetakeEnabled(1);
    policy.setRetakeScope("FAILED");
    policy.setRetakeMaxCount(1);
    policy.setRetakeScoreMode("HIGHEST");
    return policy;
  }

  private ExamCandidate failedCandidate() {
    ExamCandidate candidate = new ExamCandidate();
    candidate.setExamCandidateId(20L);
    candidate.setAssessmentExecutionId(11L);
    candidate.setUserId(7L);
    candidate.setOrganizationId(1L);
    candidate.setCandidateStatus("FAILED");
    candidate.setAttemptCount(1);
    candidate.setResultPublishedTime(LocalDateTime.now().minusHours(1));
    return candidate;
  }

  private Exam publishedExam() {
    Exam exam = new Exam();
    exam.setExamId(1L);
    exam.setOrganizationId(1L);
    exam.setPublishStatus("PUBLISHED");
    exam.setPaperVersionId(30L);
    exam.setDurationSeconds(3600);
    exam.setPassScore(new BigDecimal("6"));
    exam.setQuestionOrderMode("IMPORT");
    exam.setMultiSelectScoring("ALL_OR_NOTHING");
    return exam;
  }

  /** 已自动判分的单题作答，供成绩发布用例复用。 */
  private static ExamAnswer gradedAnswer() {
    ExamAnswer graded = new ExamAnswer();
    graded.setExamAnswerId(70L);
    graded.setExamAttemptId(40L);
    graded.setAttemptQuestionId(60L);
    graded.setFinalScore(new BigDecimal("10"));
    graded.setIsCorrect(1);
    graded.setIsAnswered(1);
    graded.setGradingStatus("AUTO_GRADED");
    return graded;
  }

  /** 自动判分的单选题，供成绩发布用例复用。 */
  private static ExamAttemptQuestion autoQuestion() {
    ExamAttemptQuestion aq = new ExamAttemptQuestion();
    aq.setAttemptQuestionId(60L);
    aq.setQuestionType("SINGLE");
    aq.setGradingMode("AUTO");
    return aq;
  }

  private AssessmentExecution standaloneExecution() {
    AssessmentExecution execution = new AssessmentExecution();
    execution.setAssessmentExecutionId(11L);
    execution.setOrganizationId(1L);
    execution.setSourceType("STANDALONE_EXAM");
    execution.setSourceBusinessId(1L);
    execution.setExamId(1L);
    execution.setAssessmentKind("FORMAL_EXAM");
    execution.setResourceType("PAPER_VERSION");
    execution.setResourceId(30L);
    execution.setDurationSecondsSnapshot(3600);
    execution.setTotalScoreSnapshot(new BigDecimal("10"));
    execution.setPassScoreSnapshot(new BigDecimal("6"));
    execution.setQuestionOrderModeSnapshot("IMPORT");
    execution.setMultiSelectScoringSnapshot("ALL_OR_NOTHING");
    return execution;
  }

  private com.sts.question.draw.QuestionDrawEngine.Candidate drawCandidate(long questionId, long versionId) {
    return new com.sts.question.draw.QuestionDrawEngine.Candidate(
      questionId, versionId, "SINGLE", "EASY", java.util.Set.of(),
      com.sts.question.draw.QuestionDrawEngine.EvaluationMode.AUTO);
  }

  private ExamAttemptQuestion attemptQuestion(long id, String type, String evaluationMode) {
    ExamAttemptQuestion question = new ExamAttemptQuestion();
    question.setAttemptQuestionId(id);
    question.setExamAttemptId(40L);
    question.setQuestionType(type);
    question.setGradingMode("ESSAY".equals(type) ? "MANUAL" : "AUTO");
    question.setEvaluationMode(evaluationMode);
    question.setScore(new BigDecimal("10"));
    question.setCorrectAnswerSnapshotJson("{\"correctOptions\":[\"A\"]}");
    return question;
  }

  private ExamAnswer answered(long id, long attemptQuestionId, String text) {
    ExamAnswer answer = new ExamAnswer();
    answer.setExamAnswerId(id);
    answer.setExamAttemptId(40L);
    answer.setAttemptQuestionId(attemptQuestionId);
    answer.setAnswerText(text);
    answer.setIsAnswered(1);
    answer.setFinalScore(BigDecimal.ZERO);
    return answer;
  }

  private AssessmentExecution trainingBankExecution() {
    AssessmentExecution execution = new AssessmentExecution();
    execution.setAssessmentExecutionId(33L);
    execution.setOrganizationId(1L);
    execution.setSourceType("TRAINING_PRACTICE");
    execution.setSourceBusinessId(90L);
    execution.setExamId(90L);
    execution.setAssessmentKind("BANK_PRACTICE");
    execution.setResourceType("QUESTION_BANK");
    execution.setResourceId(9L);
    execution.setResourceNameSnapshot("培训题库");
    execution.setDurationSecondsSnapshot(600);
    execution.setTotalScoreSnapshot(new BigDecimal("100"));
    execution.setPassScoreSnapshot(new BigDecimal("60"));
    execution.setQuestionOrderModeSnapshot("RANDOM");
    execution.setMultiSelectScoringSnapshot("ALL_OR_NOTHING");
    sealSnapshot(execution);
    return execution;
  }

  private void sealSnapshot(AssessmentExecution execution) {
    if (execution.getPolicySnapshotJson() == null) {
      execution.setPolicySnapshotJson("{}");
    }
    if (execution.getSourceBusinessId() == null) {
      execution.setSourceBusinessId(execution.getExamId());
    }
    if (execution.getResourceNameSnapshot() == null) {
      execution.setResourceNameSnapshot("测试资源");
    }
    if (execution.getMultiSelectScoringSnapshot() == null) {
      execution.setMultiSelectScoringSnapshot("ALL_OR_NOTHING");
    }
    new AssessmentExecutionSnapshotCodec(new ObjectMapper()).encode(execution);
  }

  private void assertTrainingProjectionEvent(Long trainingParticipantId, Long planContentId) {
    ArgumentCaptor<ApplicationEvent> captor = ArgumentCaptor.forClass(ApplicationEvent.class);
    verify(eventPublisher).publishEvent(captor.capture());
    assertThat(captor.getValue()).isInstanceOfSatisfying(TrainingParticipantContentFactChangedEvent.class, fact -> {
      assertThat(fact.getTrainingParticipantId()).isEqualTo(trainingParticipantId);
      assertThat(fact.getPlanContentId()).isEqualTo(planContentId);
    });
  }

  private ExamAttempt answeringAttempt() {
    ExamAttempt attempt = new ExamAttempt();
    attempt.setExamAttemptId(40L);
    attempt.setOrganizationId(1L);
    attempt.setAssessmentExecutionId(11L);
    attempt.setSourceType("STANDALONE_EXAM");
    attempt.setAssessmentKind("FORMAL_EXAM");
    attempt.setExamCandidateId(20L);
    attempt.setUserId(7L);
    attempt.setAttemptStatus("ANSWERING");
    attempt.setPassScoreSnapshot(new BigDecimal("6"));
    attempt.setMultiSelectScoringSnapshot("ALL_OR_NOTHING");
    attempt.setObjectiveScore(BigDecimal.ZERO);
    attempt.setSubjectiveScore(BigDecimal.ZERO);
    attempt.setFinalScore(BigDecimal.ZERO);
    attempt.setCorrectCount(0);
    attempt.setWrongCount(0);
    return attempt;
  }
}

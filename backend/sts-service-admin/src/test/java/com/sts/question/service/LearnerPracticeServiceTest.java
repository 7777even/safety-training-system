package com.sts.question.service;

import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sts.admin.tenant.TenantContext;
import com.sts.question.entity.*;
import com.sts.question.mapper.*;
import com.sts.question.dto.LearnerPracticeModels.Query;
import com.sts.common.api.PageRequest;
import com.sts.exam.entity.ExamAttempt;
import com.sts.exam.mapper.ExamAttemptMapper;
import com.sts.exam.service.ExamLearnerService;
import com.sts.exam.vo.*;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

/** 本人服务公开接口验证跨用户保护和模拟提交前答案裁剪。 */
@ExtendWith(MockitoExtension.class)
class LearnerPracticeServiceTest {
  @Mock LearnerPracticeSessionMapper sessionMapper;
  @Mock LearnerPracticeItemMapper itemMapper;
  @Mock QuestionUserStateMapper stateMapper;
  @InjectMocks LearnerPracticeService service;

  /** 收藏摘要不能成为未提交模拟答案的旁路，并按原题去重。 */
  @Test void favoriteQuestionsShowOwnFrozenStemWithoutAnswers() {
    var active = session(); active.setTitle("安全题库");
    var cleared = session(); cleared.setSessionId(2L); cleared.setStatus("CLEARED");
    var state = new QuestionUserState(); state.setQuestionId(5L); state.setIsFavorite(1);
    var item = new LearnerPracticeItem(); item.setSessionId(1L); item.setQuestionId(5L);
    item.setSnapshotJson("{\"questionId\":5,\"questionVersionId\":8,\"type\":\"SINGLE\",\"stem\":\"安全题干\",\"analysis\":\"秘密解析\",\"correctAnswerJson\":\"秘密答案\"}");
    when(stateMapper.selectList(any())).thenReturn(List.of(state));
    when(sessionMapper.selectList(any())).thenReturn(List.of(cleared, active));
    when(itemMapper.selectList(any())).thenReturn(List.of(item, item));
    service = new LearnerPracticeService(sessionMapper, itemMapper, stateMapper, null, null,
      null, null, null, null, null, null, null, null, null, new ObjectMapper(), null);
    try (var stp = mockStatic(StpUtil.class)) {
      stp.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
      var page = service.favorites(new PageRequest<Query>(1, 20, null));
      assertThat(page.total()).isEqualTo(1);
      assertThat(page.list().getFirst().stem()).isEqualTo("安全题干");
      assertThat(page.list().getFirst().sessionId()).isEqualTo(1L);
      assertThat(page.list().getFirst().available()).isTrue();
      assertThat(new ObjectMapper().valueToTree(page).toString()).doesNotContain("秘密", "correctAnswer", "analysis");
    }
  }

  @Test void rejectsAnotherUsersSession() {
    var session = session(); session.setUserId(8L);
    when(sessionMapper.selectById(1L)).thenReturn(session);
    try (var stp = mockStatic(StpUtil.class)) {
      stp.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
      TenantContext.setTenantId(2L);
      try { assertThatThrownBy(() -> service.get(1L)).hasMessageContaining("无权"); }
      finally { TenantContext.clear(); }
    }
  }
  @Test void simulationHidesStandardAnswerAndFeedbackUntilSubmit() {
    var session = session();
    var item = new LearnerPracticeItem(); item.setItemId(4L); item.setQuestionId(5L);
    item.setSnapshotJson("{\"questionId\":5,\"type\":\"SINGLE\",\"stem\":\"题目\",\"analysis\":\"解析\",\"correctAnswerJson\":\"{\\\"correctOptions\\\":[\\\"A\\\"]}\"}");
    item.setEvaluationResult("AUTO_CORRECT"); item.setAnswered(1);
    when(sessionMapper.selectById(1L)).thenReturn(session);
    when(itemMapper.selectList(any())).thenReturn(List.of(item));
    when(stateMapper.selectList(any())).thenReturn(List.of());
    service = new LearnerPracticeService(sessionMapper, itemMapper, stateMapper, null, null,
      null, null, null, null, null, null, null, null, null, new ObjectMapper(), null);
    try (var stp = mockStatic(StpUtil.class)) {
      stp.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
      TenantContext.setTenantId(2L);
      try {
        var hidden = service.get(1L);
        assertThat(hidden.questions().getFirst().getCorrectAnswerJson()).isNull();
        assertThat(hidden.questions().getFirst().getAnalysis()).isNull();
        assertThat(hidden.questions().getFirst().getEvaluationResult()).isNull();
        assertThat(hidden.correctCount()).isZero();
        session.setStatus("SUBMITTED");
        assertThat(service.get(1L).questions().getFirst().getAnalysis()).isEqualTo("解析");
      } finally { TenantContext.clear(); }
    }
  }
  private LearnerPracticeSession session() {
    var s = new LearnerPracticeSession(); s.setSessionId(1L); s.setUserId(7L);
    s.setOrganizationId(2L); s.setMode("SIMULATION"); s.setStatus("ANSWERING");
    s.setStartedTime(LocalDateTime.now()); return s;
  }

  /** 历史摘要保留来源和真实进度，模拟交卷前不泄露判定统计。 */
  @Test void sessionHistoryShowsProgressAndResultsOnlyWhenAllowed() {
    var session = session(); session.setTitle("错题再练"); session.setSourceQuestionId(5L);
    session.setStartedTime(LocalDateTime.of(2026, 10, 8, 10, 0));
    var correct = new LearnerPracticeItem(); correct.setQuestionId(5L); correct.setAnswered(1); correct.setEvaluationResult("AUTO_CORRECT");
    correct.setSnapshotJson("{\"questionId\":5,\"type\":\"SINGLE\",\"stem\":\"检查安全装置\",\"sourceType\":\"COURSE_QUIZ\",\"sourceName\":\"岗前培训测验\",\"correctAnswerJson\":\"隐藏答案\"}");
    var wrong = new LearnerPracticeItem(); wrong.setAnswered(1); wrong.setEvaluationResult("AUTO_WRONG");
    wrong.setSnapshotJson("{\"type\":\"JUDGE\"}");
    var essay = new LearnerPracticeItem(); essay.setAnswered(0); essay.setEvaluationResult("NOT_EVALUATED");
    essay.setSnapshotJson("{\"type\":\"ESSAY\"}");
    when(sessionMapper.selectList(any())).thenReturn(List.of(session));
    when(itemMapper.selectList(any())).thenReturn(List.of(correct, wrong, essay));
    service = new LearnerPracticeService(sessionMapper, itemMapper, stateMapper, null, null,
      null, null, null, null, null, null, null, null, null, new ObjectMapper(), null);
    try (var stp = mockStatic(StpUtil.class)) {
      stp.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
      var request = new PageRequest<Query>(1, 20, null);
      var hidden = service.sessions(request).list().getFirst();
      assertThat(hidden.totalCount()).isEqualTo(3); assertThat(hidden.answeredCount()).isEqualTo(2);
      assertThat(hidden.questionStem()).isEqualTo("检查安全装置"); assertThat(hidden.sourceName()).isEqualTo("岗前培训测验");
      assertThat(hidden.correctCount()).isZero(); assertThat(hidden.wrongCount()).isZero(); assertThat(hidden.accuracyRate()).isNull();
      assertThat(hidden.questions()).isEmpty();
      session.setStatus("SUBMITTED"); session.setSubmittedTime(LocalDateTime.of(2026, 10, 8, 10, 4));
      var submitted = service.sessions(request).list().getFirst();
      assertThat(submitted.correctCount()).isEqualTo(1); assertThat(submitted.wrongCount()).isEqualTo(1);
      assertThat(submitted.unassessedCount()).isEqualTo(1); assertThat(submitted.accuracyRate()).isEqualTo(50.0);
      assertThat(submitted.usedSeconds()).isEqualTo(240);
    }
  }

  /** 原会话清除后保留题干摘要，且取消收藏不再读取原题源。 */
  @Test void clearedFavoriteRemainsVisibleAndCanBeCancelled() {
    var session = session(); session.setStatus("CLEARED");
    var state = new QuestionUserState(); state.setQuestionId(5L); state.setIsFavorite(1); state.setHandledWrongKey("40:60");
    var item = new LearnerPracticeItem(); item.setSessionId(1L); item.setQuestionId(5L);
    item.setSnapshotJson("{\"questionId\":5,\"type\":\"SINGLE\",\"stem\":\"原收藏题目\"}");
    when(stateMapper.selectList(any())).thenReturn(List.of(state)); when(stateMapper.selectOne(any())).thenReturn(state);
    when(sessionMapper.selectList(any())).thenReturn(List.of(session)); when(itemMapper.selectList(any())).thenReturn(List.of(item));
    service = new LearnerPracticeService(sessionMapper, itemMapper, stateMapper, null, null,
      null, null, null, null, null, null, null, null, null, new ObjectMapper(), null);
    try (var stp = mockStatic(StpUtil.class)) {
      stp.when(StpUtil::getLoginIdAsLong).thenReturn(7L); TenantContext.setTenantId(2L);
      try {
        var row = service.favorites(new PageRequest<Query>(1, 20, null)).list().getFirst();
        assertThat(row.stem()).isEqualTo("原收藏题目"); assertThat(row.available()).isFalse(); assertThat(row.sessionId()).isNull();
        service.favorite(5L, false); assertThat(state.getIsFavorite()).isZero(); assertThat(state.getHandledWrongKey()).isEqualTo("40:60");
        assertThat(service.favorites(new PageRequest<Query>(1, 20, null)).total()).isZero();
      } finally { TenantContext.clear(); }
    }
  }

  @Test void handledWrongIsHiddenButNewWrongAttemptReturns() {
    var attempts = mock(ExamAttemptMapper.class); var learner = mock(ExamLearnerService.class);
    service = new LearnerPracticeService(sessionMapper, itemMapper, stateMapper, null, null,
      null, null, null, null, null, null, attempts, learner, null, new ObjectMapper(), null);
    var attempt = new ExamAttempt(); attempt.setExamAttemptId(40L); attempt.setSourceType("STANDALONE_EXAM");
    var original = new ExamAttemptQuestionVO(); original.setQuestionId(5L); original.setOptions(List.of());
    original.setCorrectAnswerSnapshotJson("{}"); original.setQuestionType("SINGLE");
    var answer = new ExamAnswerVO(); answer.setExamAnswerId(60L); answer.setIsCorrect(0); original.setAnswer(answer);
    var view = new ExamAttemptVO(); view.setQuestions(List.of(original));
    var state = new QuestionUserState(); state.setQuestionId(5L); state.setHandledWrongKey("40:60");
    when(attempts.selectList(any())).thenReturn(List.of(attempt)); when(learner.getMyAttempt(40L)).thenReturn(view);
    when(stateMapper.selectList(any())).thenReturn(List.of(state));
    try (var stp = mockStatic(StpUtil.class)) {
      stp.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
      var request = new PageRequest<Query>(1, 20, null);
      assertThat(service.wrongs(request).total()).isZero();
      // 较新来源撤销后，较旧的已处理事实不能被当成新错答重新显示。
      answer.setExamAnswerId(59L);
      assertThat(service.wrongs(request).total()).isZero();
      answer.setExamAnswerId(61L);
      assertThat(service.wrongs(request).total()).isEqualTo(1);
      original.setCorrectAnswerSnapshotJson(null);
      assertThat(service.wrongs(request).total()).isZero();
    }
  }

  @Test void savingEmptyAnswerDoesNotTurnUnansweredQuestionIntoAnswered() {
    var session = session(); session.setMode("PRACTICE");
    var item = new LearnerPracticeItem(); item.setItemId(4L); item.setSessionId(1L); item.setQuestionId(5L);
    item.setSnapshotJson("{\"type\":\"SINGLE\",\"options\":[],\"analysis\":\"解析\",\"correctAnswerJson\":\"{}\"}");
    when(sessionMapper.selectById(1L)).thenReturn(session); when(itemMapper.selectById(4L)).thenReturn(item);
    when(itemMapper.selectList(any())).thenReturn(List.of(item)); when(stateMapper.selectList(any())).thenReturn(List.of());
    service = new LearnerPracticeService(sessionMapper, itemMapper, stateMapper, null, null,
      null, null, null, null, null, null, null, null, null, new ObjectMapper(), null);
    try (var stp = mockStatic(StpUtil.class)) {
      stp.when(StpUtil::getLoginIdAsLong).thenReturn(7L); TenantContext.setTenantId(2L);
      try {
        var view = service.answer(1L, 4L, new com.sts.question.dto.LearnerPracticeModels.Answer());
        assertThat(view.questions().getFirst().getAnswer()).isNull();
        assertThat(view.questions().getFirst().getAnalysis()).isNull();
        assertThat(view.wrongCount()).isZero();
      } finally { TenantContext.clear(); }
    }
  }
  @Test void rejectsSessionFromAnotherTenantEvenWithSameUser() {
    var s = session(); s.setOrganizationId(3L); when(sessionMapper.selectById(1L)).thenReturn(s);
    try (var stp = mockStatic(StpUtil.class)) {
      stp.when(StpUtil::getLoginIdAsLong).thenReturn(7L); TenantContext.setTenantId(2L);
      try { assertThatThrownBy(() -> service.get(1L)).hasMessageContaining("无权"); }
      finally { TenantContext.clear(); }
    }
  }

  @Test void repeatedSubmitAndClearOnlyTouchPersonalSession() {
    var s = session(); s.setStatus("SUBMITTED"); when(sessionMapper.selectById(1L)).thenReturn(s);
    when(itemMapper.selectList(any())).thenReturn(List.of()); when(stateMapper.selectList(any())).thenReturn(List.of());
    service = new LearnerPracticeService(sessionMapper, itemMapper, stateMapper, null, null,
      null, null, null, null, null, null, null, null, null, new ObjectMapper(), null);
    try (var stp = mockStatic(StpUtil.class)) {
      stp.when(StpUtil::getLoginIdAsLong).thenReturn(7L); TenantContext.setTenantId(2L);
      try {
        assertThat(service.submit(1L).status()).isEqualTo("SUBMITTED");
        assertThat(service.submit(1L).status()).isEqualTo("SUBMITTED");
        verify(sessionMapper, never()).updateById(any(LearnerPracticeSession.class));
        service.clear(1L); assertThat(s.getStatus()).isEqualTo("CLEARED");
        verify(itemMapper, never()).updateById(any(LearnerPracticeItem.class));
        verify(stateMapper, never()).updateById(any(QuestionUserState.class));
        assertThatThrownBy(() -> service.get(1L)).hasMessageContaining("已清除");
      } finally { TenantContext.clear(); }
    }
  }

  @Test void repeatStartReusesTokenButCannotChangeMode() {
    var s = session(); s.setSourceQuestionId(5L); when(sessionMapper.selectOne(any())).thenReturn(s);
    when(sessionMapper.selectById(1L)).thenReturn(s);
    when(itemMapper.selectList(any())).thenReturn(List.of()); when(stateMapper.selectList(any())).thenReturn(List.of());
    service = new LearnerPracticeService(sessionMapper, itemMapper, stateMapper, null, null,
      null, null, null, null, null, null, null, null, null, new ObjectMapper(), null);
    var request = new com.sts.question.dto.LearnerPracticeModels.Start();
    request.setQuestionId(5L); request.setMode("SIMULATION"); request.setClientToken("same-token");
    try (var stp = mockStatic(StpUtil.class)) {
      stp.when(StpUtil::getLoginIdAsLong).thenReturn(7L); TenantContext.setTenantId(2L);
      try {
        assertThat(service.start(request).sessionId()).isEqualTo(1L);
        verify(sessionMapper, never()).insert(any(LearnerPracticeSession.class));
        request.setMode("PRACTICE"); assertThatThrownBy(() -> service.start(request)).hasMessageContaining("启动令牌");
      } finally { TenantContext.clear(); }
    }
  }

  @Test void essaySubmissionRemainsUnassessed() {
    var s = session(); var item = new LearnerPracticeItem(); item.setItemId(4L);
    item.setSnapshotJson("{\"type\":\"ESSAY\",\"options\":[]}"); item.setAnswered(0);
    when(sessionMapper.selectById(1L)).thenReturn(s);
    when(itemMapper.selectList(any())).thenReturn(List.of(item)); when(stateMapper.selectList(any())).thenReturn(List.of());
    service = new LearnerPracticeService(sessionMapper, itemMapper, stateMapper, null, null,
      null, null, null, null, null, null, null, null, null, new ObjectMapper(), null);
    try (var stp = mockStatic(StpUtil.class)) {
      stp.when(StpUtil::getLoginIdAsLong).thenReturn(7L); TenantContext.setTenantId(2L);
      try { var result = service.submit(1L); assertThat(result.unassessedCount()).isEqualTo(1);
        assertThat(result.wrongCount()).isZero(); assertThat(result.accuracyRate()).isNull(); }
      finally { TenantContext.clear(); }
    }
  }

  @Test void courseWrongUsesCourseContextAndInvisibleSourceIsOmitted() {
    var candidates = mock(com.sts.exam.mapper.ExamCandidateMapper.class);
    var attempts = mock(ExamAttemptMapper.class); var learner = mock(ExamLearnerService.class);
    var courses = mock(com.sts.course.service.CourseAssessmentAttemptService.class);
    service = new LearnerPracticeService(sessionMapper, itemMapper, stateMapper, candidates, null,
      null, null, null, null, null, null, attempts, learner, null, new ObjectMapper(), courses);
    var attempt = new ExamAttempt(); attempt.setExamAttemptId(40L); attempt.setExamCandidateId(30L); attempt.setSourceType("COURSE_QUIZ");
    var candidate = new com.sts.exam.entity.ExamCandidate(); candidate.setCourseEnrollmentId(3L); candidate.setCourseItemId(4L);
    when(attempts.selectList(any())).thenReturn(List.of(attempt)); when(candidates.selectById(30L)).thenReturn(candidate);
    when(stateMapper.selectList(any())).thenReturn(List.of());
    when(courses.get(3L,4L,40L)).thenThrow(new com.sts.common.exception.BusinessException(org.springframework.http.HttpStatus.FORBIDDEN,5081,"无权访问课程报名"));
    try (var stp = mockStatic(StpUtil.class)) {
      stp.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
      assertThat(service.wrongs(new PageRequest<>(1,20,null)).total()).isZero();
      verifyNoInteractions(learner);
    }
  }

  @Test void assignedPracticeFreezesSafeSnapshotAndRejectsFormalSource() {
    var candidates = mock(com.sts.exam.mapper.ExamCandidateMapper.class);
    var executions = mock(com.sts.exam.service.AssessmentExecutionService.class);
    var papers = mock(com.sts.paper.mapper.PaperVersionQuestionMapper.class);
    var versions = mock(QuestionVersionMapper.class); var options = mock(QuestionOptionMapper.class);
    var formal = mock(com.sts.exam.service.ExamAttemptServiceImpl.class);
    var candidate = new com.sts.exam.entity.ExamCandidate(); candidate.setUserId(7L); candidate.setAssessmentExecutionId(10L);
    var execution = new com.sts.exam.entity.AssessmentExecution(); execution.setAssessmentKind("SPECIAL_PRACTICE");
    execution.setResourceType("PAPER_VERSION"); execution.setResourceId(11L); execution.setResourceNameSnapshot("专项训练");
    var link = new com.sts.paper.entity.PaperVersionQuestion(); link.setQuestionVersionId(20L);
    var version = new QuestionVersion(); version.setQuestionId(5L); version.setQuestionVersionId(20L);
    version.setQuestionType("SINGLE"); version.setStem("冻结题干"); version.setAnalysis("受控解析");
    var option = new com.sts.question.entity.QuestionOption(); option.setOptionKey("A"); option.setOptionContent("安全"); option.setIsCorrect(1);
    when(candidates.selectById(30L)).thenReturn(candidate); when(executions.requireById(10L)).thenReturn(execution);
    when(papers.selectList(any())).thenReturn(List.of(link)); when(versions.selectById(20L)).thenReturn(version);
    when(options.selectList(any())).thenReturn(List.of(option)); when(formal.buildCorrectSnapshot(version)).thenReturn("{\"correctOptions\":[\"A\"]}");
    var inserted = new java.util.concurrent.atomic.AtomicReference<LearnerPracticeSession>();
    var frozen = new java.util.ArrayList<LearnerPracticeItem>();
    when(sessionMapper.insert(any(LearnerPracticeSession.class))).thenAnswer(call -> { var row = (LearnerPracticeSession)call.getArgument(0); row.setSessionId(1L); inserted.set(row); return 1; });
    when(sessionMapper.selectById(1L)).thenAnswer(call -> inserted.get());
    when(itemMapper.insert(any(LearnerPracticeItem.class))).thenAnswer(call -> { var row = (LearnerPracticeItem)call.getArgument(0); row.setItemId(4L); frozen.add(row); return 1; });
    when(itemMapper.selectList(any())).thenReturn(frozen); when(stateMapper.selectList(any())).thenReturn(List.of());
    service = new LearnerPracticeService(sessionMapper,itemMapper,stateMapper,candidates,executions,null,papers,
      versions,options,null,null,null,null,formal,new ObjectMapper(),null);
    var request = new com.sts.question.dto.LearnerPracticeModels.Start(); request.setCandidateId(30L);
    request.setMode("SIMULATION"); request.setClientToken("initial-token");
    try (var stp = mockStatic(StpUtil.class)) {
      stp.when(StpUtil::getLoginIdAsLong).thenReturn(7L); TenantContext.setTenantId(2L);
      try {
        var result = service.start(request); var question = result.questions().getFirst();
        assertThat(question.getStem()).isEqualTo("冻结题干"); assertThat(question.getCorrectAnswerJson()).isNull();
        assertThat(question.getAnalysis()).isNull(); assertThat(question.getOptions().getFirst().content()).isEqualTo("安全");
        assertThat(frozen.getFirst().getSnapshotJson()).doesNotContain("isCorrect");
        execution.setAssessmentKind("FORMAL_EXAM");
        assertThatThrownBy(() -> service.start(request)).hasMessageContaining("正式考试试卷不能");
        candidate.setUserId(8L);
        assertThatThrownBy(() -> service.start(request)).hasMessageContaining("无权");
      } finally { TenantContext.clear(); }
    }
  }

}

package com.sts.question;

import com.sts.admin.support.*;
import com.sts.admin.tenant.TenantContext;
import com.sts.question.entity.*;
import com.sts.question.mapper.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import static org.assertj.core.api.Assertions.*;

/** 真MySQL验证个人练习落库、令牌唯一性与租户隔离。 */
@SpringBootTest(classes = LearnerBatchMapperItConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class LearnerPracticeMapperIT extends AbstractMysqlIntegrationTest {
  @Autowired LearnerPracticeSessionMapper sessions;
  @Autowired LearnerPracticeItemMapper items;
  @Autowired QuestionUserStateMapper states;
  @Autowired com.sts.exam.mapper.ExamAttemptMapper attempts;
  /** 真实SQL验证收藏/摘要查询只读取本人及本机构记录。 */
  @Test void favoriteAndSessionSummariesStayWithinOwnerAndTenant() {
    var own = new LearnerPracticeSession(); own.setUserId(700L); own.setMode("SIMULATION"); own.setStatus("ANSWERING");
    own.setTitle("错题再练"); own.setSourceQuestionId(20L); own.setClientToken("it-own-summary"); own.setStartedTime(LocalDateTime.now()); sessions.insert(own);
    var other = new LearnerPracticeSession(); other.setUserId(701L); other.setMode("PRACTICE"); other.setStatus("ANSWERING");
    other.setTitle("他人会话"); other.setClientToken("it-other-summary"); other.setStartedTime(LocalDateTime.now()); sessions.insert(other);
    for (var session : java.util.List.of(own, other)) {
      var item = new LearnerPracticeItem(); item.setSessionId(session.getSessionId()); item.setQuestionId(20L); item.setQuestionVersionId(30L);
      item.setSnapshotJson("{\"questionId\":20,\"questionVersionId\":30,\"type\":\"SINGLE\",\"stem\":\"" + session.getTitle() + "题干\",\"sourceName\":\"岗前测验\",\"sourceType\":\"COURSE_QUIZ\"}");
      item.setAnswered(1); item.setEvaluationResult("AUTO_CORRECT"); item.setSortOrder(0); items.insert(item);
    }
    states.ensurePersonalState(TenantContext.requireTenantId(), 700L, 20L);
    var state = states.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<QuestionUserState>().eq(QuestionUserState::getUserId, 700L));
    state.setIsFavorite(1); states.updateById(state);
    var service = new com.sts.question.service.LearnerPracticeService(sessions, items, states, null, null,
      null, null, null, null, null, null, null, null, null, new com.fasterxml.jackson.databind.ObjectMapper(), null);
    try (var stp = org.mockito.Mockito.mockStatic(cn.dev33.satoken.stp.StpUtil.class)) {
      stp.when(cn.dev33.satoken.stp.StpUtil::getLoginIdAsLong).thenReturn(700L);
      var query = new com.sts.question.dto.LearnerPracticeModels.Query(); query.setKeyword("错题再练题干");
      var request = new com.sts.common.api.PageRequest<>(1, 1, query);
      var favorite = service.favorites(request); assertThat(favorite.total()).isEqualTo(1);
      assertThat(favorite.list().getFirst().sessionId()).isEqualTo(own.getSessionId());
      var summary = service.sessions(request); assertThat(summary.total()).isEqualTo(1);
      assertThat(summary.list().getFirst().totalCount()).isEqualTo(1); assertThat(summary.list().getFirst().answeredCount()).isEqualTo(1);
      assertThat(summary.list().getFirst().correctCount()).isZero(); assertThat(summary.list().getFirst().questions()).isEmpty();
      assertThat(TenantContext.callWithTenant(999999L, () -> service.favorites(request).total())).isZero();
      assertThat(TenantContext.callWithTenant(999999L, () -> service.sessions(request).total())).isZero();
      service.favorite(20L, false); assertThat(service.favorites(request).total()).isZero();
    }
  }
  @Test void sessionAndItemRoundTripAndTenantIsolation() {
    var s = new LearnerPracticeSession(); s.setUserId(700L); s.setMode("SIMULATION"); s.setStatus("ANSWERING");
    s.setClientToken("it-practice-token"); s.setStartedTime(LocalDateTime.now()); sessions.insert(s);
    var item = new LearnerPracticeItem(); item.setSessionId(s.getSessionId()); item.setQuestionId(20L); item.setQuestionVersionId(30L);
    item.setSnapshotJson("{\"stem\":\"冻结题目\"}"); item.setAnswered(0); item.setSortOrder(0); items.insert(item);
    assertThat(items.selectById(item.getItemId()).getSnapshotJson()).contains("冻结题目");
    assertThat(TenantContext.callWithTenant(999999L, () -> sessions.selectById(s.getSessionId()))).isNull();
    var duplicate = new LearnerPracticeSession(); duplicate.setUserId(700L); duplicate.setMode("SIMULATION");
    duplicate.setStatus("ANSWERING"); duplicate.setClientToken("it-practice-token"); duplicate.setStartedTime(LocalDateTime.now());
    assertThatThrownBy(() -> sessions.insert(duplicate)).isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
  }
  @Test void wrongDispositionRoundTripsWithoutDeletingOriginalState() {
    var state = new QuestionUserState(); state.setUserId(700L); state.setQuestionId(20L);
    state.setIsFavorite(1); state.setWrongCount(2); state.setCorrectCount(0); state.setHandledWrongKey("40:60");
    state.setWrongDisposition("MASTERED"); states.insert(state);
    states.ensurePersonalState(TenantContext.requireTenantId(),700L,20L);
    states.ensurePersonalState(TenantContext.requireTenantId(),700L,20L);
    var found = states.selectById(state.getUserQuestionStateId());
    assertThat(found.getHandledWrongKey()).isEqualTo("40:60"); assertThat(found.getIsFavorite()).isEqualTo(1);
    assertThat(found.getWrongCount()).isEqualTo(2);
  }
  @Test void actualHistoricalQueryMergesBothSourcesAndLatestSnapshot() {
    var formal = history("STANDALONE_EXAM",30L,700L); attempts.insert(formal);
    var course = history("COURSE_QUIZ",31L,700L); attempts.insert(course);
    var other = history("STANDALONE_EXAM",32L,701L); attempts.insert(other);
    var learner = org.mockito.Mockito.mock(com.sts.exam.service.ExamLearnerService.class);
    var courseService = org.mockito.Mockito.mock(com.sts.course.service.CourseAssessmentAttemptService.class);
    var candidateMapper = org.mockito.Mockito.mock(com.sts.exam.mapper.ExamCandidateMapper.class);
    var candidate = new com.sts.exam.entity.ExamCandidate(); candidate.setCourseEnrollmentId(3L); candidate.setCourseItemId(4L);
    org.mockito.Mockito.when(candidateMapper.selectById(31L)).thenReturn(candidate);
    org.mockito.Mockito.when(learner.getMyAttempt(formal.getExamAttemptId())).thenReturn(wrongView(50L));
    org.mockito.Mockito.when(courseService.get(3L,4L,course.getExamAttemptId())).thenReturn(wrongView(60L));
    var service = new com.sts.question.service.LearnerPracticeService(sessions,items,states,candidateMapper,null,
      null,null,null,null,null,null,attempts,learner,null,new com.fasterxml.jackson.databind.ObjectMapper(),courseService);
    try (var stp = org.mockito.Mockito.mockStatic(cn.dev33.satoken.stp.StpUtil.class)) {
      stp.when(cn.dev33.satoken.stp.StpUtil::getLoginIdAsLong).thenReturn(700L);
      var result = service.wrongs(new com.sts.common.api.PageRequest<>(1,1,null));
      assertThat(result.total()).isEqualTo(1); assertThat(result.list().getFirst().getQuestionVersionId()).isEqualTo(60L);
      assertThat(result.list().getFirst().getSourceType()).isEqualTo("COURSE_QUIZ");
      org.mockito.Mockito.verify(learner, org.mockito.Mockito.never()).getMyAttempt(other.getExamAttemptId());
    }
  }
  /** 只构造历史查询需要的正式事实，判定可见性仍由原领域公开接口裁剪。 */
  private com.sts.exam.entity.ExamAttempt history(String source,Long candidate,Long user) {
    var row = new com.sts.exam.entity.ExamAttempt(); row.setOrganizationId(TenantContext.requireTenantId());
    row.setAssessmentExecutionId(10L); row.setSourceType(source); row.setAssessmentKind("FORMAL_EXAM");
    row.setExamCandidateId(candidate); row.setUserId(user); row.setPaperSourceType("PAPER_VERSION"); row.setPaperSourceId(20L);
    row.setDurationSecondsSnapshot(3600); row.setQuestionOrderModeSnapshot("IMPORT"); row.setMultiSelectScoringSnapshot("ALL_OR_NOTHING");
    row.setAttemptNo(1); row.setAttemptType("FIRST"); row.setAttemptStatus("PUBLISHED");
    row.setClientAttemptToken("it-history-" + candidate); return row;
  }
  private com.sts.exam.vo.ExamAttemptVO wrongView(Long version) {
    var answer = new com.sts.exam.vo.ExamAnswerVO(); answer.setExamAnswerId(version); answer.setIsCorrect(0);
    var question = new com.sts.exam.vo.ExamAttemptQuestionVO(); question.setQuestionId(20L); question.setQuestionVersionId(version);
    question.setQuestionType("SINGLE"); question.setOptions(java.util.List.of()); question.setAnswer(answer); question.setCorrectAnswerSnapshotJson("{}");
    var view = new com.sts.exam.vo.ExamAttemptVO(); view.setQuestions(java.util.List.of(question)); return view;
  }

}

package com.sts.question.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sts.admin.tenant.TenantContext;
import com.sts.common.api.*;
import com.sts.common.exception.BusinessException;
import com.sts.exam.entity.*;
import com.sts.exam.mapper.*;
import com.sts.exam.service.*;
import com.sts.exam.vo.*;
import com.sts.paper.mapper.PaperVersionQuestionMapper;
import com.sts.paper.entity.PaperVersionQuestion;
import com.sts.question.dto.LearnerPracticeModels.*;
import com.sts.question.dto.LearnerPracticeModels.Question;
import com.sts.question.entity.*;
import com.sts.question.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import com.sts.course.service.CourseAssessmentAttemptService;
import java.time.*;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/** 本人自由练习：冻结题源、隔离正式考核，并从可见原答卷派生错题。 */
@Service
@RequiredArgsConstructor
public class LearnerPracticeService {
  /** 个人练习会话持久化。 */
  private final LearnerPracticeSessionMapper sessionMapper;
  /** 个人练习冻结题目持久化。 */
  private final LearnerPracticeItemMapper itemMapper;
  /** 个人题目收藏及错题处理状态。 */
  private final QuestionUserStateMapper stateMapper;
  /** 本人分配及课程考核人员关联。 */
  private final ExamCandidateMapper candidateMapper;
  /** 已发布考核执行及题源引用。 */
  private final AssessmentExecutionService executionService;
  /** 原考试发布状态。 */
  private final ExamMapper examMapper;
  /** 原试卷版本的冻结题目关联。 */
  private final PaperVersionQuestionMapper paperMapper;
  /** 不可变题目版本。 */
  private final QuestionVersionMapper versionMapper;
  /** 冻结版本选项。 */
  private final QuestionOptionMapper optionMapper;
  /** 已有题库候选加载。 */
  private final QuestionCandidatePoolService poolService;
  /** 已发布执行的冻结题池。 */
  private final AssessmentExecutionQuestionPoolService executionPoolService;
  /** 本人历史正式作答事实。 */
  private final ExamAttemptMapper attemptMapper;
  /** 原考试答卷可见性裁剪。 */
  private final ExamLearnerService learnerService;
  /** 复用服务端标准答案快照装配。 */
  private final ExamAttemptServiceImpl attemptService;
  /** 冻结快照序列化。 */
  private final ObjectMapper json;
  /** 原课程报名/课程项作答授权。 */
  private final CourseAssessmentAttemptService courseAttempts;

  /** 仅列出本人已分配的专项或题库练习，不包含未考正式试卷。 */
  public PageResult<Task> tasks(PageRequest<Query> request) {
    List<Task> rows = new ArrayList<>();
    for (ExamCandidate candidate : candidateMapper.selectList(new LambdaQueryWrapper<ExamCandidate>()
      .eq(ExamCandidate::getUserId, StpUtil.getLoginIdAsLong()).orderByDesc(ExamCandidate::getExamCandidateId))) {
      AssessmentExecution execution = executionService.requireById(candidate.getAssessmentExecutionId());
      if (!practiceKind(execution)) continue;
      if (execution.getExamId() != null) {
        Exam exam = examMapper.selectById(execution.getExamId());
        if (exam == null || !Set.of("PUBLISHED", "RUNNING", "ENDED").contains(exam.getPublishStatus())) continue;
      }
      rows.add(new Task(candidate.getExamCandidateId(), execution.getResourceNameSnapshot(), execution.getAssessmentKind()));
    }
    return page(rows.stream().filter(row -> matches(row.title(), request)).toList(), request);
  }

  /** 新建独立会话；启动令牌在本人机构内唯一，重复请求返回原会话。 */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public View start(Start request) {
    if ((request.getCandidateId() == null) == (request.getQuestionId() == null)) throw invalid("请选择一个练习或错题来源");
    Long userId = StpUtil.getLoginIdAsLong();
    var tokenQuery = new LambdaQueryWrapper<LearnerPracticeSession>()
      .eq(LearnerPracticeSession::getUserId, userId).eq(LearnerPracticeSession::getClientToken, request.getClientToken());
    LearnerPracticeSession existing = sessionMapper.selectOne(tokenQuery);
    if (existing != null) {
      if (!Objects.equals(existing.getCandidateId(), request.getCandidateId())
        || !Objects.equals(existing.getSourceQuestionId(), request.getQuestionId())
        || !Objects.equals(existing.getMode(), request.getMode())) throw invalid("启动令牌已用于其他题源或模式");
      return get(existing.getSessionId());
    }
    List<Question> questions;
    String title;
    if (request.getCandidateId() != null) {
      AssessmentExecution execution = requirePractice(request.getCandidateId());
      title = execution.getResourceNameSnapshot();
      questions = freeze(execution);
      for (Question question : questions) { question.setSourceType(execution.getAssessmentKind()); question.setSourceName(title); }
    } else {
      Question wrong = visibleWrong(request.getQuestionId());
      questions = List.of(wrong); title = "错题再练";
    }
    if (questions.isEmpty()) throw invalid("练习没有可用题目");
    LearnerPracticeSession session = new LearnerPracticeSession();
    session.setOrganizationId(TenantContext.requireTenantId()); session.setUserId(userId);
    session.setCandidateId(request.getCandidateId()); session.setSourceQuestionId(request.getQuestionId());
    session.setMode(request.getMode()); session.setClientToken(request.getClientToken());
    session.setTitle(title); session.setStatus("ANSWERING"); session.setStartedTime(LocalDateTime.now());
    try { sessionMapper.insert(session); }
    catch (DuplicateKeyException duplicate) {
      return start(request);
    }
    int order = 0;
    for (Question question : questions) {
      question.setAnswer(null); question.setEvaluationResult(null);
      LearnerPracticeItem item = new LearnerPracticeItem();
      item.setOrganizationId(session.getOrganizationId()); item.setSessionId(session.getSessionId());
      item.setQuestionId(question.getQuestionId()); item.setQuestionVersionId(question.getQuestionVersionId());
      item.setSnapshotJson(write(question)); item.setAnswered(0); item.setSortOrder(order++);
      itemMapper.insert(item);
    }
    return get(session.getSessionId());
  }

  /** 恢复本人会话，模拟提交前裁剪所有答案与统计。 */
  public View get(Long sessionId) {
    return view(owned(sessionId), true);
  }

  /** 列表与详情共用冻结快照统计和反馈裁剪，列表不输出题目正文及答案。 */
  private View view(LearnerPracticeSession session, boolean includeQuestions) {
    Long sessionId = session.getSessionId();
    boolean simulationHidden = "SIMULATION".equals(session.getMode()) && !"SUBMITTED".equals(session.getStatus());
    Map<Long, QuestionUserState> states = includeQuestions ? states() : Map.of();
    List<Question> questions = new ArrayList<>();
    int correct = 0, wrong = 0, unassessed = 0, answered = 0;
    String stem = null, sourceType = session.getSourceQuestionId() == null ? "PRACTICE" : "WRONG_QUESTION", sourceName = session.getTitle();
    List<LearnerPracticeItem> items = items(sessionId);
    for (LearnerPracticeItem item : items) {
      Question question = read(item.getSnapshotJson(), Question.class);
      if (Objects.equals(item.getAnswered(), 1)) answered++;
      if (questions.isEmpty()) {
        if (session.getSourceQuestionId() != null) stem = question.getStem();
        if (question.getSourceType() != null) sourceType = question.getSourceType();
        if (question.getSourceName() != null) sourceName = question.getSourceName();
      }
      question.setItemId(item.getItemId());
      question.setAnswer(item.getAnswerJson() == null || !Objects.equals(item.getAnswered(), 1) ? null : read(item.getAnswerJson(), Answer.class));
      question.setEvaluationResult(item.getEvaluationResult());
      question.setFavorite(includeQuestions && states.containsKey(question.getQuestionId()) && Objects.equals(states.get(question.getQuestionId()).getIsFavorite(), 1));
      boolean feedback = !simulationHidden && (Objects.equals(item.getAnswered(), 1) || "SUBMITTED".equals(session.getStatus()));
      if (!feedback) { question.setAnalysis(null); question.setCorrectAnswerJson(null); question.setEvaluationResult(null); }
      else if ("AUTO_CORRECT".equals(item.getEvaluationResult())) correct++;
      else if ("AUTO_WRONG".equals(item.getEvaluationResult())) wrong++;
      else unassessed++;
      questions.add(question);
    }
    long seconds = Duration.between(session.getStartedTime(), session.getSubmittedTime() == null ? LocalDateTime.now() : session.getSubmittedTime()).getSeconds();
    return new View(sessionId, session.getCandidateId(), session.getSourceQuestionId(), session.getTitle(), session.getMode(), session.getStatus(), session.getStartedTime(),
      session.getSubmittedTime(), correct, wrong, unassessed, correct + wrong == 0 ? null : correct * 100.0 / (correct + wrong),
      Math.max(0, seconds), includeQuestions ? questions : List.of(), items.size(), answered, stem, sourceType, sourceName);
  }

  /** 会话列表供刷新恢复及模拟历史回看；不返回题目和服务端标准答案。 */
  public PageResult<View> sessions(PageRequest<Query> request) {
    var rows = sessionMapper.selectList(new LambdaQueryWrapper<LearnerPracticeSession>()
      .eq(LearnerPracticeSession::getUserId, StpUtil.getLoginIdAsLong())
      .ne(LearnerPracticeSession::getStatus, "CLEARED").orderByDesc(LearnerPracticeSession::getSessionId));
    // ponytail: 当前沿用本人内存分页；大量历史会话时改为数据库分页和批量摘要查询。
    return page(rows.stream().map(s -> view(s, false)).filter(s -> matches(s.title(), request)
      || matches(s.questionStem(), request) || matches(s.sourceName(), request)).toList(), request);
  }

  /** 保存单题答案；练习反馈立即可见，模拟到交卷后统一可见。 */
  @Transactional
  public View answer(Long sessionId, Long itemId, Answer answer) {
    lock(sessionId);
    LearnerPracticeSession session = owned(sessionId);
    if (!"ANSWERING".equals(session.getStatus())) throw invalid("会话已结束，不能修改答案");
    LearnerPracticeItem item = itemMapper.selectById(itemId);
    if (item == null || !Objects.equals(item.getSessionId(), sessionId)) throw denied();
    Question question = read(item.getSnapshotJson(), Question.class);
    Set<String> keys = question.getOptions().stream().map(Option::key).collect(Collectors.toSet());
    if (!keys.containsAll(answer.getSelected()) || answer.getBlanks().values().stream().anyMatch(v -> v == null || v.length() > 10000))
      throw invalid("答案包含无效选项或空位");
    boolean answered = answer.getText() != null && !answer.getText().isBlank() || !answer.getSelected().isEmpty()
      || answer.getBlanks().values().stream().anyMatch(value -> !value.isBlank());
    item.setAnswerJson(write(answer)); item.setAnswered(answered ? 1 : 0); item.setEvaluationResult(evaluate(question, answer));
    itemMapper.updateById(item);
    return get(sessionId);
  }

  /** 幂等交卷，只更新个人会话，不调用正式考试提交或成绩投影。 */
  @Transactional
  public View submit(Long sessionId) {
    lock(sessionId);
    LearnerPracticeSession session = owned(sessionId);
    if ("SUBMITTED".equals(session.getStatus())) return get(sessionId);
    for (LearnerPracticeItem item : items(sessionId)) {
      if (item.getEvaluationResult() == null) {
        item.setEvaluationResult(evaluate(read(item.getSnapshotJson(), Question.class), new Answer()));
        itemMapper.updateById(item);
      }
    }
    session.setStatus("SUBMITTED"); session.setSubmittedTime(LocalDateTime.now()); sessionMapper.updateById(session);
    return get(sessionId);
  }

  /** 清除当前个人练习记录；原考试/课程答卷、错题和收藏均不改动。 */
  @Transactional
  public void clear(Long sessionId) {
    lock(sessionId);
    LearnerPracticeSession session = sessionMapper.selectById(sessionId);
    if (session == null || !Objects.equals(session.getUserId(), StpUtil.getLoginIdAsLong())) throw denied();
    session.setStatus("CLEARED"); sessionMapper.updateById(session);
  }

  /** 从受控历史答卷读取错题，个人处理状态与正式事实分离。 */
  public PageResult<Question> wrongs(PageRequest<Query> request) {
    Map<Long, QuestionUserState> states = states();
    List<Question> rows = new ArrayList<>();
    for (Question question : wrongFacts().values()) {
      QuestionUserState state = states.get(question.getQuestionId());
      if (state != null && state.getHandledWrongKey() != null
        && wrongRecordId(question.getWrongKey()) <= wrongRecordId(state.getHandledWrongKey())) continue;
      question.setFavorite(state != null && Objects.equals(state.getIsFavorite(), 1));
      if (matches(question.getStem(), request)) rows.add(question);
    }
    return page(rows, request);
  }

  /** 删除或已掌握仅记录当前错题事实水位，新错答自动重新出现。 */
  @Transactional
  public void disposition(Long questionId, String value) {
    Question question = visibleWrong(questionId);
    QuestionUserState state = state(questionId);
    state.setHandledWrongKey(question.getWrongKey()); state.setWrongDisposition(value); stateMapper.updateById(state);
  }

  /** 按原题去重展示本人收藏，摘要不携带任何答案或解析。 */
  public PageResult<FavoriteQuestion> favorites(PageRequest<Query> request) {
    var favoriteIds = states().values().stream().filter(s -> Objects.equals(s.getIsFavorite(), 1))
      .map(QuestionUserState::getQuestionId).collect(Collectors.toSet());
    if (favoriteIds.isEmpty()) return page(List.of(), request);
    var sessions = sessionMapper.selectList(new LambdaQueryWrapper<LearnerPracticeSession>()
      .eq(LearnerPracticeSession::getUserId, StpUtil.getLoginIdAsLong()).orderByDesc(LearnerPracticeSession::getSessionId))
      .stream().collect(Collectors.toMap(LearnerPracticeSession::getSessionId, s -> s));
    Map<Long, FavoriteQuestion> rows = new LinkedHashMap<>();
    if (!sessions.isEmpty()) {
      for (var item : itemMapper.selectList(new LambdaQueryWrapper<LearnerPracticeItem>()
        .in(LearnerPracticeItem::getSessionId, sessions.keySet()).in(LearnerPracticeItem::getQuestionId, favoriteIds)
        .orderByDesc(LearnerPracticeItem::getItemId))) {
        var session = sessions.get(item.getSessionId());
        if (session == null || !favoriteIds.contains(item.getQuestionId())) continue;
        boolean available = !"CLEARED".equals(session.getStatus());
        var previous = rows.get(item.getQuestionId());
        if (previous != null && (previous.available() || !available)) continue;
        Question question = read(item.getSnapshotJson(), Question.class);
        rows.put(item.getQuestionId(), new FavoriteQuestion(item.getQuestionId(), question.getQuestionVersionId(),
          question.getType(), question.getStem(), question.getSourceType(),
          question.getSourceName() == null ? session.getTitle() : question.getSourceName(),
          available ? session.getSessionId() : null, available));
      }
    }
    // 无个人会话的旧收藏仍按原答卷可见性取摘要，失效资源仅保留取消入口。
    Map<Long, Question> wrongs = rows.keySet().containsAll(favoriteIds) ? Map.of() : wrongFacts();
    for (Long id : favoriteIds.stream().sorted(Comparator.reverseOrder()).toList()) {
      if (rows.containsKey(id)) continue;
      Question question = wrongs.get(id);
      rows.put(id, question == null ? new FavoriteQuestion(id, null, null, "题目当前不可访问", null, null, null, false)
        : new FavoriteQuestion(id, question.getQuestionVersionId(), question.getType(), question.getStem(),
          question.getSourceType(), question.getSourceName(), null, true));
    }
    return page(rows.values().stream().filter(q -> matches(q.stem(), request)).toList(), request);
  }

  /** 收藏仅允许本人会话或可见错题中出现的题目，不接受外部userId。 */
  @Transactional
  public void favorite(Long questionId, boolean favorite) {
    // 取消本人已有收藏不依赖题源仍可访问，清除练习后仍可整理收藏。
    if (!favorite) {
      if (!states().containsKey(questionId)) return;
      QuestionUserState state = state(questionId); state.setIsFavorite(0); stateMapper.updateById(state); return;
    }
    List<Long> sessionIds = sessionMapper.selectList(new LambdaQueryWrapper<LearnerPracticeSession>()
      .eq(LearnerPracticeSession::getUserId, StpUtil.getLoginIdAsLong())).stream().map(LearnerPracticeSession::getSessionId).toList();
    boolean accessible = !sessionIds.isEmpty() && itemMapper.selectCount(new LambdaQueryWrapper<LearnerPracticeItem>()
      .in(LearnerPracticeItem::getSessionId, sessionIds).eq(LearnerPracticeItem::getQuestionId, questionId)) > 0;
    if (!accessible) visibleWrong(questionId);
    QuestionUserState state = state(questionId); state.setIsFavorite(favorite ? 1 : 0); stateMapper.updateById(state);
  }

  /** 仅允许本人已分配且已发布的练习题源。 */
  private AssessmentExecution requirePractice(Long candidateId) {
    ExamCandidate candidate = candidateMapper.selectById(candidateId);
    if (candidate == null || !Objects.equals(candidate.getUserId(), StpUtil.getLoginIdAsLong())) throw denied();
    AssessmentExecution execution = executionService.requireById(candidate.getAssessmentExecutionId());
    if (!practiceKind(execution)) throw invalid("正式考试试卷不能用于自由练习");
    if (execution.getExamId() != null) {
      Exam exam = examMapper.selectById(execution.getExamId());
      if (exam == null || !Set.of("PUBLISHED", "RUNNING", "ENDED").contains(exam.getPublishStatus())) throw denied();
    }
    return execution;
  }

  /** 识别已确认可用于自由练习的两种题源。 */
  private boolean practiceKind(AssessmentExecution execution) {
    return Set.of("SPECIAL_PRACTICE", "BANK_PRACTICE").contains(execution.getAssessmentKind());
  }

  /** 复用已冻结题池、抽题规则和答案快照，生成独立会话题目。 */
  private List<Question> freeze(AssessmentExecution execution) {
    List<Long> versionIds;
    if ("PAPER_VERSION".equals(execution.getResourceType())) {
      versionIds = paperMapper.selectList(new LambdaQueryWrapper<PaperVersionQuestion>()
        .eq(PaperVersionQuestion::getPaperVersionId, execution.getResourceId()).orderByAsc(PaperVersionQuestion::getSortOrder))
        .stream().map(PaperVersionQuestion::getQuestionVersionId).toList();
    } else {
      String hash;
      try { hash = json.readTree(execution.getPolicySnapshotJson() == null ? "{}" : execution.getPolicySnapshotJson()).path("questionPoolHash").asText(""); }
      catch (Exception error) { throw invalid("练习题源快照无法解析"); }
      var candidates = hash.isBlank() ? poolService.loadCandidates(execution.getResourceId())
        : executionPoolService.loadCandidates(execution.getAssessmentExecutionId(), hash);
      var rules = QuestionBankDrawEngine.parseRules(execution.getPolicySnapshotJson(), json);
      versionIds = rules.isEmpty() ? candidates.stream().map(c -> c.questionVersionId()).toList()
        : QuestionBankDrawEngine.draw(candidates.stream().map(c -> new QuestionBankDrawEngine.Candidate(c.questionId(), c.questionVersionId(),
          c.questionType(), c.difficulty(), c.knowledgePointIds(), c.evaluationMode())).toList(), rules, ThreadLocalRandom.current().nextLong())
          .questions().stream().map(QuestionBankDrawEngine.DrawnQuestion::questionVersionId).toList();
    }
    return versionIds.stream().map(id -> {
      QuestionVersion version = versionMapper.selectById(id);
      if (version == null) throw invalid("冻结题目版本缺失");
      Question question = new Question(); question.setQuestionId(version.getQuestionId()); question.setQuestionVersionId(id);
      question.setType(version.getQuestionType()); question.setStem(version.getStem()); question.setAnalysis(version.getAnalysis());
      question.setCorrectAnswerJson(attemptService.buildCorrectSnapshot(version));
      question.setOptions(optionMapper.selectList(new LambdaQueryWrapper<QuestionOption>().eq(QuestionOption::getQuestionVersionId, id)
        .orderByAsc(QuestionOption::getSortOrder)).stream().map(o -> new Option(o.getOptionKey(), o.getOptionContent())).toList());
      question.setBlankCount(blankCount(question.getCorrectAnswerJson())); return question;
    }).toList();
  }

  /** 从原领域受控历史答卷提取明确错误事实。 */
  private Map<Long, Question> wrongFacts() {
    Map<Long, Question> result = new LinkedHashMap<>();
    // ponytail: 本人历史答卷逐份使用既有裁剪接口；大量历史记录时改为批量裁剪查询。
    var attempts = attemptMapper.selectList(new LambdaQueryWrapper<ExamAttempt>().eq(ExamAttempt::getUserId, StpUtil.getLoginIdAsLong())
      .in(ExamAttempt::getSourceType, List.of("STANDALONE_EXAM", "TRAINING_EXAM", "COURSE_QUIZ"))
      .in(ExamAttempt::getAttemptStatus, List.of("PUBLISHED", "RESULT_EFFECTIVE", "SUBMITTED", "PENDING_PUBLISH", "PENDING_REVIEW"))
      .orderByDesc(ExamAttempt::getExamAttemptId));
    for (ExamAttempt attempt : attempts) {
      ExamAttemptVO view;
      try {
        if ("COURSE_QUIZ".equals(attempt.getSourceType())) {
          ExamCandidate candidate = candidateMapper.selectById(attempt.getExamCandidateId());
          if (candidate == null || candidate.getCourseEnrollmentId() == null || candidate.getCourseItemId() == null) continue;
          view = courseAttempts.get(candidate.getCourseEnrollmentId(), candidate.getCourseItemId(), attempt.getExamAttemptId());
        } else view = learnerService.getMyAttempt(attempt.getExamAttemptId());
      } catch (BusinessException unavailable) {
        // 已撤销或不可访问的历史来源不生成错题入口，其他业务错误继续上抛。
        if (Set.of(HttpStatus.FORBIDDEN, HttpStatus.NOT_FOUND, HttpStatus.BAD_REQUEST).contains(unavailable.getStatus())) continue;
        throw unavailable;
      }
      for (ExamAttemptQuestionVO original : view.getQuestions()) {
        if (original.getAnswer() == null || !Objects.equals(original.getAnswer().getIsCorrect(), 0)
          || original.getCorrectAnswerSnapshotJson() == null) continue;
        Question question = new Question(); question.setQuestionId(original.getQuestionId()); question.setQuestionVersionId(original.getQuestionVersionId());
        question.setItemId(original.getQuestionId()); question.setType(original.getQuestionType()); question.setStem(original.getStemSnapshot());
        question.setAnalysis(original.getAnalysisSnapshot()); question.setCorrectAnswerJson(original.getCorrectAnswerSnapshotJson());
        question.setBlankCount(original.getBlankCount());
        question.setOptions(original.getOptions().stream().map(o -> new Option(o.getOptionKey(), o.getOptionContentSnapshot())).toList());
        question.setSourceType(attempt.getSourceType()); question.setSourceName(view.getResourceName());
        question.setWrongKey(attempt.getExamAttemptId() + ":" + original.getAnswer().getExamAnswerId());
        // 同题按错误答案记录水位选择快照，较旧来源恢复可见不会伪装成新错答。
        result.compute(question.getQuestionId(), (id, previous) -> previous == null
          || wrongRecordId(question.getWrongKey()) > wrongRecordId(previous.getWrongKey()) ? question : previous);
      }
    }
    return result;
  }

  /** 取得本人仍可查看的错题快照。 */
  private Question visibleWrong(Long questionId) {
    Question question = wrongFacts().get(questionId);
    if (question == null) throw denied(); return question;
  }
  /** 错误答案ID为本机构数据库的单调事实水位；标识仅由服务端生成。 */
  private long wrongRecordId(String key) { return Long.parseLong(key.substring(key.indexOf(':') + 1)); }
  /** 读取本人题目收藏和处理水位。 */
  private Map<Long, QuestionUserState> states() {
    return stateMapper.selectList(new LambdaQueryWrapper<QuestionUserState>().eq(QuestionUserState::getUserId, StpUtil.getLoginIdAsLong()))
      .stream().collect(Collectors.toMap(QuestionUserState::getQuestionId, s -> s));
  }
  /** 原子初始化并锁定本人题目状态。 */
  private QuestionUserState state(Long questionId) {
    var query = new LambdaQueryWrapper<QuestionUserState>().eq(QuestionUserState::getUserId, StpUtil.getLoginIdAsLong()).eq(QuestionUserState::getQuestionId, questionId);
    // 原子初始化后锁定已有关系，避免两个首次处理请求竞争插入或覆盖彼此字段。
    stateMapper.ensurePersonalState(TenantContext.requireTenantId(), StpUtil.getLoginIdAsLong(), questionId);
    return stateMapper.selectOne(query.last("FOR UPDATE"));
  }
  /** 校验会话的本人及机构归属，拒绝已清除记录。 */
  private LearnerPracticeSession owned(Long id) {
    LearnerPracticeSession session = sessionMapper.selectById(id);
    if (session == null || !Objects.equals(session.getUserId(), StpUtil.getLoginIdAsLong())
      || !Objects.equals(session.getOrganizationId(), TenantContext.requireTenantId())) throw denied();
    if ("CLEARED".equals(session.getStatus())) throw invalid("该个人练习记录已清除"); return session;
  }
  /** 按本人会话加行锁，串行作答、提交与清除。 */
  private void lock(Long id) {
    sessionMapper.selectOne(new LambdaQueryWrapper<LearnerPracticeSession>().eq(LearnerPracticeSession::getSessionId, id)
      .eq(LearnerPracticeSession::getUserId, StpUtil.getLoginIdAsLong()).last("FOR UPDATE"));
  }
  /** 按冻结顺序读取当前会话题目。 */
  private List<LearnerPracticeItem> items(Long id) {
    return itemMapper.selectList(new LambdaQueryWrapper<LearnerPracticeItem>().eq(LearnerPracticeItem::getSessionId, id).orderByAsc(LearnerPracticeItem::getSortOrder));
  }
  /** 仅判定客观题，简答仅记录而不评分。 */
  private String evaluate(Question question, Answer answer) {
    return "ESSAY".equals(question.getType()) ? "NOT_EVALUATED"
      : ObjectiveAnswerEvaluator.correct(question.getType(), question.getCorrectAnswerJson(), answer.getSelected(), answer.getBlanks(), answer.getText())
        ? "AUTO_CORRECT" : "AUTO_WRONG";
  }
  /** 从冻结答案提取填空数量，兼容旧单空题。 */
  private int blankCount(String snapshot) {
    try { return Math.max(1, json.readTree(snapshot).path("blanks").size()); } catch (Exception error) { throw invalid("答案快照损坏"); }
  }
  /** 根据本人列表关键词匹配名称或题干。 */
  private boolean matches(String value, PageRequest<Query> request) {
    return request.getQuery() == null || request.getQuery().getKeyword() == null || request.getQuery().getKeyword().isBlank()
      || value != null && value.contains(request.getQuery().getKeyword());
  }
  /** 按已受控列表返回当前分页。 */
  private <T> PageResult<T> page(List<T> rows, PageRequest<?> request) {
    int start = (int)Math.min((long)(request.getPageNum() - 1) * request.getPageSize(), rows.size());
    return new PageResult<>(rows.subList(start, Math.min(start + request.getPageSize(), rows.size())), rows.size());
  }
  /** 序列化个人会话持久化快照。 */
  private String write(Object value) { try { return json.writeValueAsString(value); } catch (Exception error) { throw invalid("练习记录保存失败"); } }
  /** 反序列化服务端保存的冻结快照。 */
  private <T> T read(String value, Class<T> type) { try { return json.readValue(value, type); } catch (Exception error) { throw invalid("练习快照无法读取"); } }
  /** 返回已同步契约的状态冲突错误。 */
  private BusinessException invalid(String message) { return new BusinessException(HttpStatus.CONFLICT, 7091, message); }
  /** 返回统一本人资源拒绝错误。 */
  private BusinessException denied() { return new BusinessException(HttpStatus.FORBIDDEN, 7092, "无权访问该个人练习或错题"); }
}

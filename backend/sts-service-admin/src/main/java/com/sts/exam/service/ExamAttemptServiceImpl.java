package com.sts.exam.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sts.certificate.service.CertificateAutoIssueService;
import com.sts.common.exception.BusinessException;
import com.sts.common.service.BaseServiceImpl;
import com.sts.course.dto.CourseAssessmentResultEffective;
import com.sts.course.dto.CourseAssessmentSubmitted;
import com.sts.course.service.CourseLearningService;
import com.sts.exam.dto.*;
import com.sts.exam.entity.*;
import com.sts.exam.mapper.*;
import com.sts.exam.vo.*;
import com.sts.paper.entity.PaperVersion;
import com.sts.paper.entity.PaperVersionQuestion;
import com.sts.paper.mapper.PaperVersionMapper;
import com.sts.paper.mapper.PaperVersionQuestionMapper;
import com.sts.question.entity.QuestionBlankAnswer;
import com.sts.question.entity.QuestionOption;
import com.sts.question.entity.QuestionVersion;
import com.sts.question.mapper.QuestionBlankAnswerMapper;
import com.sts.question.mapper.QuestionOptionMapper;
import com.sts.question.mapper.QuestionVersionMapper;
import com.sts.question.service.QuestionCandidatePoolService;
import com.sts.training.event.TrainingParticipantContentFactChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExamAttemptServiceImpl
  extends BaseServiceImpl<ExamAttemptMapper, ExamAttempt>
  implements ExamAttemptService {

  private final ExamMapper examMapper;
  private final ExamCandidateMapper examCandidateMapper;
  private final ExamPolicyMapper examPolicyMapper;
  private final ExamAttemptMapper examAttemptMapper;
  private final ExamAttemptQuestionMapper attemptQuestionMapper;
  private final ExamAttemptOptionMapper attemptOptionMapper;
  private final ExamAnswerMapper examAnswerMapper;
  private final ExamAnswerItemMapper examAnswerItemMapper;
  private final ExamReviewRecordMapper reviewRecordMapper;
  private final ExamReviewerMapper examReviewerMapper;
  private final ExamScorePublishLogMapper scorePublishLogMapper;
  private final ExamAnticheatEventMapper anticheatEventMapper;
  private final ExamPracticeResetMapper practiceResetMapper;
  private final PaperVersionMapper paperVersionMapper;
  private final PaperVersionQuestionMapper paperVersionQuestionMapper;
  private final QuestionVersionMapper questionVersionMapper;
  private final QuestionOptionMapper questionOptionMapper;
  private final QuestionBlankAnswerMapper blankAnswerMapper;
  private final ObjectMapper objectMapper;
  private final ExamCandidateProfileSnapshotAssembler profileSnapshotAssembler;
  private final AssessmentExecutionService assessmentExecutionService;
  private final QuestionCandidatePoolService candidatePoolService;
  private final AssessmentExecutionQuestionPoolService executionQuestionPoolService;
  private final CourseLearningService courseLearningService;
  private final ApplicationEventPublisher eventPublisher;
  private final CertificateAutoIssueService certificateAutoIssueService;
  /** 新作答知识点冻结，不回填历史题目。 */
  private final AttemptKnowledgeSnapshotService knowledgeSnapshotService;

  @Override
  @Transactional
  public ExamAttemptVO start(StartAttemptRequest request) {
    return startInternal(request, false);
  }

  /**
   * @param retake 仅补考接口传入 true；通用开考不得凭请求创建 RETAKE。
   */
  private ExamAttemptVO startInternal(StartAttemptRequest request, boolean retake) {
    AssessmentExecution execution;
    Exam exam;
    if (request.getAssessmentExecutionId() != null) {
      execution = assessmentExecutionService.requireById(request.getAssessmentExecutionId());
      exam = execution.getExamId() == null ? null
        : retake ? requireExam(execution.getExamId()) : requirePublishedExam(execution.getExamId());
    } else {
      exam = retake ? requireExam(request.getExamId()) : requirePublishedExam(request.getExamId());
      execution = assessmentExecutionService.ensureStandalone(exam);
    }
    assertOnlineAnswerable(execution);
    boolean finalAssessment = isFinalCourseAssessment(execution);
    if (finalAssessment) {
      if (!Objects.equals(execution.getCourseOfferingId(), request.getCourseOfferingId())
        || !Objects.equals(execution.getCourseItemId(), request.getCourseItemId())) {
        throw new BusinessException(HttpStatus.FORBIDDEN, 5081, "结业执行与课程报名不匹配");
      }
      courseLearningService.assertFinalAssessmentReady(request.getCourseEnrollmentId(), request.getCourseItemId(), request.getUserId());
    }
    ExamCandidate candidate = retake
      ? requireExistingCandidate(execution, request)
      : findOrCreateCandidate(execution, request);
    if (finalAssessment) {
      // 锁定同一考生，串行判断首测/补测并创建答卷；首次并发建考生由既有唯一键收敛。
      candidate = examCandidateMapper.selectOne(new LambdaQueryWrapper<ExamCandidate>()
        .eq(ExamCandidate::getExamCandidateId, candidate.getExamCandidateId()).last("FOR UPDATE"));
    }
    ExamAttempt byToken = examAttemptMapper.selectOne(new LambdaQueryWrapper<ExamAttempt>()
      .eq(ExamAttempt::getExamCandidateId, candidate.getExamCandidateId())
      .eq(ExamAttempt::getClientAttemptToken, request.getClientAttemptToken())
      .last(finalAssessment ? "FOR UPDATE" : ""));
    if (byToken != null) {
      notifyTrainingProjection(candidate, exam);
      return toVO(byToken, true);
    }
    ExamAttempt answering = examAttemptMapper.selectOne(new LambdaQueryWrapper<ExamAttempt>()
      .eq(ExamAttempt::getExamCandidateId, candidate.getExamCandidateId())
      .in(ExamAttempt::getAttemptStatus, List.of("CREATED", "ANSWERING"))
      .last(finalAssessment ? "LIMIT 1 FOR UPDATE" : "LIMIT 1"));
    if (answering != null) {
      notifyTrainingProjection(candidate, exam);
      return toVO(answering, true);
    }
    ExamAttempt terminal = findLatestReusableTerminal(candidate.getExamCandidateId(), finalAssessment);
    if (finalAssessment && candidate.getAttemptCount() > 0 && terminal == null) {
      throw new BusinessException(HttpStatus.CONFLICT, 5088, "首次生效失败后才允许一次补测");
    }
    if (!retake && terminal != null) {
      boolean repeatable = isRepeatablePractice(execution) || (finalAssessment
        && candidate.getAttemptCount() == 1 && "RESULT_EFFECTIVE".equals(terminal.getAttemptStatus())
        && Integer.valueOf(0).equals(terminal.getIsPassed()));
      boolean trainingLimitReached = "TRAINING_PRACTICE".equals(execution.getSourceType())
        && candidate.getAttemptCount() >= policyInt(execution.getPolicySnapshotJson(), "maxAttempts", 1);
      if (!repeatable || trainingLimitReached) {
        if (promoteLegacyObjectiveTrainingExam(terminal, candidate, exam)) {
          return toVO(terminal, true);
        }
        notifyTrainingProjection(candidate, exam);
        return toVO(terminal, true);
      }
    }
    if (retake) {
      assertCanCreateRetake(exam, candidate);
    }

    int attemptNo = candidate.getAttemptCount() + 1;
    LocalDateTime now = LocalDateTime.now();
    ExamAttempt attempt = new ExamAttempt();
    attempt.setAssessmentExecutionId(execution.getAssessmentExecutionId());
    attempt.setSourceType(execution.getSourceType());
    attempt.setAssessmentKind(execution.getAssessmentKind());
    attempt.setExamCandidateId(candidate.getExamCandidateId());
    attempt.setUserId(request.getUserId());
    attempt.setPaperVersionId("PAPER_VERSION".equals(execution.getResourceType()) ? execution.getResourceId() : null);
    attempt.setPaperSourceType(execution.getResourceType());
    attempt.setPaperSourceId(execution.getResourceId());
    attempt.setTotalScoreSnapshot(execution.getTotalScoreSnapshot());
    attempt.setPassScoreSnapshot(execution.getPassScoreSnapshot());
    attempt.setDurationSecondsSnapshot(execution.getDurationSecondsSnapshot());
    attempt.setQuestionOrderModeSnapshot(execution.getQuestionOrderModeSnapshot());
    attempt.setMultiSelectScoringSnapshot(execution.getMultiSelectScoringSnapshot());
    attempt.setAttemptNo(attemptNo);
    attempt.setAttemptType(finalAssessment && attemptNo == 2 ? "RETAKE" : derivedAttemptType(retake, execution));
    attempt.setAttemptStatus("ANSWERING");
    attempt.setStartedTime(now);
    attempt.setDeadlineTime(execution.getDurationSecondsSnapshot() == null
                              || execution.getDurationSecondsSnapshot() <= 0 ? null
                              : now.plusSeconds(execution.getDurationSecondsSnapshot()));
    attempt.setUsedSeconds(0);
    attempt.setObjectiveScore(BigDecimal.ZERO);
    attempt.setSubjectiveScore(BigDecimal.ZERO);
    attempt.setFinalScore(BigDecimal.ZERO);
    attempt.setCorrectCount(0);
    attempt.setWrongCount(0);
    attempt.setPlatform(request.getPlatform());
    attempt.setOperatingSystem(request.getOperatingSystem());
    attempt.setBrowser(request.getBrowser());
    attempt.setClientIp(request.getClientIp());
    attempt.setClientAttemptToken(request.getClientAttemptToken());
    if (isUnscoredQuestionBankPractice(execution)) {
      attempt.setTotalScoreSnapshot(null);
      attempt.setPassScoreSnapshot(null);
      attempt.setObjectiveScore(null);
      attempt.setSubjectiveScore(null);
      attempt.setFinalScore(null);
    }
    try {
      examAttemptMapper.insert(attempt);
    } catch (DuplicateKeyException ex) {
      ExamAttempt existing = examAttemptMapper.selectOne(new LambdaQueryWrapper<ExamAttempt>()
        .eq(ExamAttempt::getExamCandidateId, candidate.getExamCandidateId())
        .eq(ExamAttempt::getClientAttemptToken, request.getClientAttemptToken()));
      if (existing != null) {
        return toVO(existing, true);
      }
      throw ex;
    }
    if ("QUESTION_BANK".equals(execution.getResourceType())) {
      freezeQuestionBank(attempt, execution);
    } else {
      freezeQuestions(attempt, execution.getResourceId());
    }
    candidate.setAttemptCount(attemptNo);
    candidate.setCandidateStatus("IN_PROGRESS");
    examCandidateMapper.updateById(candidate);
    notifyTrainingProjection(candidate, exam);
    return toVO(attempt, true);
  }

  @Override
  public ExamAttemptVO get(Long examAttemptId) {
    ExamAttempt attempt = requireAttempt(examAttemptId);
    ExamAttemptVO vo = toVO(attempt, true);
    vo.setPredictedIsPassed(predictIsPassed(attempt));
    vo.setRetakeEligibility(evaluateRetakeEligibility(
      examForExecution(attempt.getAssessmentExecutionId()),
      examCandidateMapper.selectById(attempt.getExamCandidateId())));
    return vo;
  }

  @Override
  @Transactional
  public void saveAnswers(Long examAttemptId, SaveAnswersRequest request) {
    ExamAttempt attempt = requireAttempt(examAttemptId);
    assertAnswering(attempt);
    LocalDateTime now = LocalDateTime.now();
    for (SaveAnswerRequest ans : request.getAnswers()) {
      ExamAttemptQuestion aq = attemptQuestionMapper.selectById(ans.getAttemptQuestionId());
      if (aq == null || !aq.getExamAttemptId().equals(examAttemptId)) {
        throw new BusinessException(HttpStatus.BAD_REQUEST, 7022, "作答题目不存在");
      }
      ExamAnswer answer = examAnswerMapper.selectOne(new LambdaQueryWrapper<ExamAnswer>()
        .eq(ExamAnswer::getExamAttemptId, examAttemptId)
        .eq(ExamAnswer::getAttemptQuestionId, ans.getAttemptQuestionId()));
      if (answer == null) {
        answer = new ExamAnswer();
        answer.setExamAttemptId(examAttemptId);
        answer.setAttemptQuestionId(ans.getAttemptQuestionId());
        answer.setFinalScore(StringUtils.hasText(aq.getEvaluationMode()) ? null : BigDecimal.ZERO);
        answer.setGradingStatus("UNANSWERED");
        answer.setIsAnswered(0);
        examAnswerMapper.insert(answer);
      }
      answer.setAnswerText(ans.getAnswerText());
      boolean answered = StringUtils.hasText(ans.getAnswerText())
        || (ans.getItems() != null && !ans.getItems().isEmpty());
      answer.setIsAnswered(answered ? 1 : 0);
      answer.setAnsweredTime(answered ? now : answer.getAnsweredTime());
      answer.setLastSavedTime(now);
      examAnswerMapper.updateById(answer);

      examAnswerItemMapper.delete(new LambdaQueryWrapper<ExamAnswerItem>()
        .eq(ExamAnswerItem::getExamAnswerId, answer.getExamAnswerId()));
      // physical delete for soft-delete entity - use helper by setting deleted
      // Class A answer_item: use soft delete via update; for replace, physical via mapper delete may set deleted=0 wrongly.
      // Pragmatic: insert new items with unique keys; first soft-delete old.
      softDeleteAnswerItems(answer.getExamAnswerId());
      if (ans.getItems() != null) {
        for (SaveAnswerItemRequest item : ans.getItems()) {
          ExamAnswerItem entity = new ExamAnswerItem();
          entity.setExamAnswerId(answer.getExamAnswerId());
          entity.setItemType(item.getItemType());
          entity.setItemKey(item.getItemKey());
          entity.setItemValue(item.getItemValue());
          entity.setSortOrder(item.getSortOrder());
          examAnswerItemMapper.insert(entity);
        }
      }
    }
  }

  @Override
  @Transactional
  public ExamAttemptVO submit(Long examAttemptId, SubmitAttemptRequest request) {
    ExamAttempt attempt = requireAttempt(examAttemptId);
    if (isTerminal(attempt.getAttemptStatus())) {
      return toVO(attempt, true);
    }
    assertAnswering(attempt);
    if (isDeadlineReached(attempt)) {
      attempt.setSubmitReason("TIMEOUT");
      return finalizeSubmit(attempt);
    }
    if ("COURSE_BANK_PRACTICE".equals(attempt.getSourceType()) || isUnscoredQuestionBankPractice(attempt)) {
      assertAllQuestionsAnswered(attempt.getExamAttemptId());
    }
    attempt.setSubmitReason(request.getSubmitReason() == null ? "MANUAL" : request.getSubmitReason());
    if (request.getUsedSeconds() != null) {
      attempt.setUsedSeconds(request.getUsedSeconds());
    }
    return finalizeSubmit(attempt);
  }

  @Override
  @Transactional
  public ExamAttemptVO timeoutSubmit(Long examAttemptId) {
    ExamAttempt attempt = requireAttempt(examAttemptId);
    if (isTerminal(attempt.getAttemptStatus())) {
      return toVO(attempt, true);
    }
    if (isUnscoredQuestionBankPractice(attempt)) {
      assertAllQuestionsAnswered(attempt.getExamAttemptId());
    }
    attempt.setSubmitReason("TIMEOUT");
    return finalizeSubmit(attempt);
  }

  @Override
  @Transactional
  public void reviewAnswer(Long examAnswerId, ReviewAnswerRequest request) {
    reviewAnswerInternal(null, null, examAnswerId, request, false);
  }

  @Override
  @Transactional
  public void reviewCourseAnswer(Long courseOfferingId, Long courseItemId,
                                 Long examAnswerId, ReviewAnswerRequest request) {
    reviewAnswerInternal(courseOfferingId, courseItemId, examAnswerId, request, true);
  }

  private void reviewAnswerInternal(Long courseOfferingId, Long courseItemId,
                                    Long examAnswerId, ReviewAnswerRequest request,
                                    boolean courseRoute) {
    ExamAnswer answer = examAnswerMapper.selectById(examAnswerId);
    if (answer == null) {
      throw new BusinessException(HttpStatus.NOT_FOUND, 7031, "答案不存在");
    }
    ExamAttempt attempt = requireAttempt(answer.getExamAttemptId());
    // 正式成绩发布后评分事实不可变，前端限制不能替代服务端状态护栏。
    if ("PUBLISHED".equals(attempt.getAttemptStatus()) && attempt.getScorePublishedTime() != null) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 7032, "正式成绩已发布，不可批阅或改分");
    }
    if (!List.of("PENDING_REVIEW", "PENDING_PUBLISH", "SUBMITTED").contains(attempt.getAttemptStatus())) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 7032, "当前尝试状态不可批阅");
    }
    AssessmentExecution execution = assessmentExecutionService.requireById(attempt.getAssessmentExecutionId());
    boolean courseQuiz = "COURSE_QUIZ".equals(execution.getSourceType());
    if (courseRoute != courseQuiz
      || (courseRoute && (!Objects.equals(courseOfferingId, execution.getCourseOfferingId())
      || !Objects.equals(courseItemId, execution.getCourseItemId())))) {
      throw new BusinessException(HttpStatus.FORBIDDEN, 7033, "批阅入口与考核来源不匹配");
    }
    Long reviewerUserId = StpUtil.getLoginIdAsLong();
    Long reviewerCount = examReviewerMapper.selectCount(new LambdaQueryWrapper<ExamReviewer>()
                                                          .eq(ExamReviewer::getAssessmentExecutionId,
                                                              execution.getAssessmentExecutionId())
                                                          .eq(ExamReviewer::getReviewerUserId, reviewerUserId));
    if (reviewerCount == null || reviewerCount == 0) {
      throw new BusinessException(HttpStatus.FORBIDDEN, 7033, "当前教师未分配该考核批阅任务");
    }
    BigDecimal before = answer.getManualScore() != null ? answer.getManualScore() : answer.getFinalScore();
    ExamReviewRecord record = new ExamReviewRecord();
    record.setExamAttemptId(attempt.getExamAttemptId());
    record.setExamAnswerId(examAnswerId);
    record.setReviewerUserId(reviewerUserId);
    record.setBeforeScore(before);
    record.setAfterScore(request.getAfterScore());
    record.setReviewComment(request.getReviewComment());
    record.setReviewAction(request.getReviewAction() == null ? "SCORE" : request.getReviewAction());
    record.setReviewedTime(LocalDateTime.now());
    reviewRecordMapper.insert(record);

    answer.setManualScore(request.getAfterScore());
    answer.setFinalScore(request.getAfterScore());
    answer.setGradingStatus("MANUAL_GRADED");
    examAnswerMapper.updateById(answer);
    recomputeAttemptScores(attempt);
    if (!hasPendingManual(attempt.getExamAttemptId())) {
      if (courseQuiz) {
        markCourseResultEffective(attempt);
      } else {
        attempt.setAttemptStatus("PENDING_PUBLISH");
        attempt.setReviewCompletedTime(LocalDateTime.now());
      }
      examAttemptMapper.updateById(attempt);
      if (courseQuiz) {
        ExamCandidate candidate = examCandidateMapper.selectById(attempt.getExamCandidateId());
        if (candidate != null) {
          updateCandidateAfterEffective(candidate, attempt);
          examCandidateMapper.updateById(candidate);
          courseLearningService.applyEffectiveAssessmentResult(toCourseAssessmentResult(candidate, attempt));
        }
      } else {
        // 最后一题批阅完成后，培训结果需要从“待阅卷”推进到“待发布成绩”。
        ExamCandidate candidate = examCandidateMapper.selectById(attempt.getExamCandidateId());
        if (candidate != null) {
          candidate.setCandidateStatus("PENDING_PUBLISH");
          candidate.setFinalAttemptId(attempt.getExamAttemptId());
          candidate.setFinalScore(attempt.getFinalScore());
          examCandidateMapper.updateById(candidate);
          notifyTrainingProjection(candidate, examForExecution(attempt.getAssessmentExecutionId()));
        }
      }
    }
  }

  @Override
  @Transactional
  public ExamAttemptVO publishScore(Long examAttemptId, Long publishedBy) {
    ExamAttempt attempt = requireAttempt(examAttemptId);
    ExamCandidate candidate = examCandidateMapper.selectById(attempt.getExamCandidateId());
    AssessmentExecution execution = assessmentExecutionService.requireById(attempt.getAssessmentExecutionId());
    if (execution.getExamId() == null) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 7042, "课程考核成绩不支持人工发布");
    }
    if ("PUBLISHED".equals(attempt.getAttemptStatus())) {
      return toVO(attempt, true);
    }
    // 题库练习交卷即生效，不进入教师发布成绩流程；保留幂等返回避免旧页面重复调用。
    if ("RESULT_EFFECTIVE".equals(attempt.getAttemptStatus())
      && "BANK_PRACTICE".equals(attempt.getAssessmentKind())) {
      return toVO(attempt, true);
    }
    if (hasPendingManual(examAttemptId)) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 7041, "仍有主观题未批阅");
    }
    recomputeAttemptScores(attempt);
    attempt.setAttemptStatus("PUBLISHED");
    attempt.setScorePublishedTime(LocalDateTime.now());
    Integer passed = attempt.getFinalScore() != null && attempt.getPassScoreSnapshot() != null
      ? attempt.getFinalScore().compareTo(attempt.getPassScoreSnapshot()) >= 0 ? 1 : 0 : null;
    attempt.setIsPassed(passed);
    examAttemptMapper.updateById(attempt);

    ExamScorePublishLog log = new ExamScorePublishLog();
    log.setExamId(execution.getExamId());
    log.setExamAttemptId(examAttemptId);
    log.setPublishedBy(publishedBy == null ? 0L : publishedBy);
    log.setPublishedTime(LocalDateTime.now());
    log.setPublishType("SINGLE");
    log.setScoreSnapshot(attempt.getFinalScore());
    log.setResultSnapshot(passed == null ? "NOT_APPLICABLE" : passed == 1 ? "PASSED" : "FAILED");
    scorePublishLogMapper.insert(log);

    if (candidate != null) {
      updateCandidateAfterPublish(candidate, attempt);
      notifyTrainingProjection(candidate, examForExecution(attempt.getAssessmentExecutionId()));
    }
    return toVO(attempt, true);
  }

  @Override
  @Transactional
  public ExamAttemptVO retake(Long examCandidateId, String clientAttemptToken) {
    ExamCandidate candidate = examCandidateMapper.selectById(examCandidateId);
    if (candidate == null) {
      throw new BusinessException(HttpStatus.NOT_FOUND, 7011, "考试人员不存在");
    }
    StartAttemptRequest req = new StartAttemptRequest();
    req.setAssessmentExecutionId(candidate.getAssessmentExecutionId());
    req.setUserId(candidate.getUserId());
    req.setCourseOfferingId(candidate.getCourseOfferingId());
    req.setCourseItemId(candidate.getCourseItemId());
    req.setCourseEnrollmentId(candidate.getCourseEnrollmentId());
    req.setClientAttemptToken(clientAttemptToken);
    req.setAttemptType("RETAKE");
    return startInternal(req, true);
  }

  @Override
  @Transactional
  public void practiceReset(Long examCandidateId, PracticeResetRequest request) {
    ExamCandidate candidate = examCandidateMapper.selectById(examCandidateId);
    if (candidate == null) {
      throw new BusinessException(HttpStatus.NOT_FOUND, 7011, "考试人员不存在");
    }
    Exam exam = examForExecution(candidate.getAssessmentExecutionId());
    if (exam == null) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 7053, "课程考核不支持独立练习重置");
    }
    if (!"SPECIAL_PRACTICE".equals(exam.getExamType()) && !"BANK_PRACTICE".equals(exam.getExamType())) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 7053, "仅练习类考试可重置");
    }
    List<ExamAttempt> attempts = examAttemptMapper.selectList(new LambdaQueryWrapper<ExamAttempt>()
      .eq(ExamAttempt::getExamCandidateId, examCandidateId)
      .in(ExamAttempt::getAttemptStatus, List.of("CREATED", "ANSWERING")));
    for (ExamAttempt attempt : attempts) {
      attempt.setAttemptStatus("VOID");
      examAttemptMapper.updateById(attempt);
    }
    candidate.setCandidateStatus("NOT_STARTED");
    examCandidateMapper.updateById(candidate);

    ExamPracticeReset reset = new ExamPracticeReset();
    reset.setExamId(exam.getExamId());
    reset.setExamCandidateId(examCandidateId);
    reset.setUserId(candidate.getUserId());
    reset.setResetScope(request.getResetScope());
    reset.setResetTime(LocalDateTime.now());
    reset.setReason(request.getReason());
    practiceResetMapper.insert(reset);
  }

  @Override
  @Transactional
  public void recordAnticheat(Long examAttemptId, AnticheatEventRequest request) {
    ExamAttempt attempt = requireAttempt(examAttemptId);
    ExamAnticheatEvent event = new ExamAnticheatEvent();
    event.setExamAttemptId(examAttemptId);
    event.setEventType(request.getEventType());
    event.setEventTime(request.getEventTime());
    event.setEventCount(request.getEventCount());
    event.setActionTaken(request.getActionTaken());
    event.setEvidenceFileId(request.getEvidenceFileId());
    event.setClientPayloadJson(request.getClientPayloadJson());
    anticheatEventMapper.insert(event);
  }

  @Override
  public boolean hasPublishedPass(Long examId, Long userId) {
    AssessmentExecution execution = assessmentExecutionService.requireStandaloneByExamId(examId);
    List<ExamCandidate> candidates = examCandidateMapper.selectList(new LambdaQueryWrapper<ExamCandidate>()
                                                                      .eq(ExamCandidate::getAssessmentExecutionId,
                                                                          execution.getAssessmentExecutionId())
      .eq(ExamCandidate::getUserId, userId));
    return candidates.stream().anyMatch(c ->
      "PASSED".equals(c.getCandidateStatus()) && c.getResultPublishedTime() != null);
  }

  private ExamAttemptVO finalizeSubmit(ExamAttempt attempt) {
    attempt.setSubmittedTime(LocalDateTime.now());
    attempt.setAttemptStatus("SUBMITTED");
    examAttemptMapper.updateById(attempt);
    if (isUnscoredQuestionBankPractice(attempt)) {
      return finalizeUnscoredTrainingPractice(attempt);
    }
    autoGrade(attempt);
    recomputeAttemptScores(attempt);
    boolean pendingManual = hasPendingManual(attempt.getExamAttemptId());
    if ("COURSE_BANK_PRACTICE".equals(attempt.getSourceType())
      || ("COURSE_QUIZ".equals(attempt.getSourceType()) && !pendingManual)) {
      markCourseResultEffective(attempt);
    } else if (pendingManual) {
      attempt.setAttemptStatus("PENDING_REVIEW");
    } else if ("TRAINING_EXAM".equals(attempt.getSourceType())) {
      markTrainingExamScorePublished(attempt);
    } else {
      attempt.setAttemptStatus("PENDING_PUBLISH");
      attempt.setReviewCompletedTime(LocalDateTime.now());
    }
    examAttemptMapper.updateById(attempt);
    ExamCandidate candidate = examCandidateMapper.selectById(attempt.getExamCandidateId());
    if (candidate != null) {
      if ("RESULT_EFFECTIVE".equals(attempt.getAttemptStatus())) {
        updateCandidateAfterEffective(candidate, attempt);
      } else if ("PUBLISHED".equals(attempt.getAttemptStatus())) {
        updateCandidateAfterPublish(candidate, attempt);
      } else {
        candidate.setCandidateStatus(attempt.getAttemptStatus());
        examCandidateMapper.updateById(candidate);
      }
      if ("RESULT_EFFECTIVE".equals(attempt.getAttemptStatus())) {
        courseLearningService.applyEffectiveAssessmentResult(toCourseAssessmentResult(candidate, attempt));
      } else if ("PENDING_REVIEW".equals(attempt.getAttemptStatus())
        && "COURSE_QUIZ".equals(attempt.getSourceType())) {
        // 课程测验交卷即投影“已参加、待批阅”的内容项进度，成绩生效后再转为已完成。
        courseLearningService.applySubmittedAssessmentResult(toCourseAssessmentSubmitted(candidate, attempt));
      }
      notifyTrainingProjection(candidate, examForExecution(attempt.getAssessmentExecutionId()));
    }
    return toVO(attempt, true);
  }

  /** 题库练习客观题自动形成得分并立即生效，简答题只记录且不进入批阅。 */
  private ExamAttemptVO finalizeUnscoredTrainingPractice(ExamAttempt attempt) {
    List<ExamAttemptQuestion> questions = attemptQuestionMapper.selectList(
      new LambdaQueryWrapper<ExamAttemptQuestion>()
        .eq(ExamAttemptQuestion::getExamAttemptId, attempt.getExamAttemptId()));
    Map<Long, ExamAnswer> answers = examAnswerMapper.selectList(
        new LambdaQueryWrapper<ExamAnswer>().eq(ExamAnswer::getExamAttemptId, attempt.getExamAttemptId()))
      .stream().collect(Collectors.toMap(ExamAnswer::getAttemptQuestionId, Function.identity()));
    int evaluated = 0;
    int correct = 0;
    int unassessed = 0;
    for (ExamAttemptQuestion question : questions) {
      ExamAnswer answer = answers.get(question.getAttemptQuestionId());
      if (answer == null || answer.getIsAnswered() == null || answer.getIsAnswered() != 1) {
        throw new BusinessException(HttpStatus.BAD_REQUEST, 7027, "练习仍有未作答题目，不能提交");
      }
      if ("RECORD_ONLY".equals(question.getEvaluationMode())) {
        answer.setAutoScore(null);
        answer.setManualScore(null);
        answer.setFinalScore(null);
        answer.setIsCorrect(null);
        answer.setGradingStatus("NOT_EVALUATED");
        answer.setEvaluationResult("NOT_EVALUATED");
        unassessed++;
      } else {
        GradeResult grade = gradeObjective(question, answer, attempt.getMultiSelectScoringSnapshot());
        answer.setAutoScore(grade.score());
        answer.setManualScore(null);
        answer.setFinalScore(grade.score());
        answer.setIsCorrect(grade.correct() ? 1 : 0);
        answer.setGradingStatus("AUTO_GRADED");
        answer.setEvaluationResult(grade.correct() ? "AUTO_CORRECT" : "AUTO_WRONG");
        evaluated++;
        if (grade.correct()) {
          correct++;
        }
      }
      examAnswerMapper.updateById(answer);
    }
    LocalDateTime now = LocalDateTime.now();
    recomputeAttemptScores(attempt);
    attempt.setAttemptStatus("RESULT_EFFECTIVE");
    attempt.setReviewCompletedTime(now);
    attempt.setResultEffectiveTime(now);
    attempt.setSubjectiveScore(null);
    attempt.setIsPassed(null);
    attempt.setEvaluatedCount(evaluated);
    attempt.setCorrectCount(correct);
    attempt.setWrongCount(evaluated - correct);
    attempt.setUnassessedCount(unassessed);
    attempt.setAccuracyRate(evaluated == 0 ? null
                              : BigDecimal.valueOf(correct).multiply(BigDecimal.valueOf(100))
      .divide(BigDecimal.valueOf(evaluated), 2, RoundingMode.HALF_UP));
    examAttemptMapper.updateById(attempt);
    ExamCandidate candidate = examCandidateMapper.selectById(attempt.getExamCandidateId());
    if (candidate != null) {
      candidate.setCandidateStatus("COMPLETED");
      candidate.setFinalScore(attempt.getFinalScore());
      if (candidate.getBestScore() == null || (attempt.getFinalScore() != null
        && attempt.getFinalScore().compareTo(candidate.getBestScore()) > 0)) {
        candidate.setBestScore(attempt.getFinalScore());
      }
      candidate.setFinalAttemptId(attempt.getExamAttemptId());
      candidate.setResultEffectiveTime(now);
      examCandidateMapper.updateById(candidate);
      notifyTrainingProjection(candidate, examForExecution(attempt.getAssessmentExecutionId()));
    }
    return toVO(attempt, true);
  }

  private boolean isUnscoredQuestionBankPractice(ExamAttempt attempt) {
    return ("TRAINING_PRACTICE".equals(attempt.getSourceType()) && attempt.getTotalScoreSnapshot() == null)
      || ("STANDALONE_PRACTICE".equals(attempt.getSourceType()) && "BANK_PRACTICE".equals(attempt.getAssessmentKind()));
  }

  private boolean isUnscoredQuestionBankPractice(AssessmentExecution execution) {
    return "BANK_PRACTICE".equals(execution.getAssessmentKind())
      && ("STANDALONE_PRACTICE".equals(execution.getSourceType())
      || "TRAINING_PRACTICE".equals(execution.getSourceType()));
  }

  /**
   * 线下考试没有可作答的题目快照，成绩由管理端录入，学员不得在线开考。
   * <p>
   * 历史缺陷：该路径会把 examId 当作 paperVersionId 去查题，两表 ID 空间重叠时可能冻结到无关试卷的题目。
   */
  private void assertOnlineAnswerable(AssessmentExecution execution) {
    if ("OFFLINE_RESULT".equals(execution.getResourceType())) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 7072, "线下考试不支持在线作答");
    }
  }

  private CourseAssessmentResultEffective toCourseAssessmentResult(ExamCandidate candidate,
                                                                   ExamAttempt attempt) {
    return new CourseAssessmentResultEffective(
      candidate.getOrganizationId(),
      attempt.getAssessmentExecutionId(),
      attempt.getExamAttemptId(),
      candidate.getExamCandidateId(),
      attempt.getUserId(),
      candidate.getCourseEnrollmentId(),
      candidate.getCourseOfferingId(),
      candidate.getCourseItemId(),
      attempt.getSourceType(),
      attempt.getAttemptNo(),
      attempt.getResultEffectiveTime() == null ? attempt.getSubmittedTime() : attempt.getResultEffectiveTime(),
      attempt.getFinalScore(),
      attempt.getIsPassed(),
      attempt.getCorrectCount(),
      attempt.getWrongCount());
  }

  /**
   * 把课程测验的交卷事实投影给课程域；只带参与事实，不带任何成绩结论。
   */
  private CourseAssessmentSubmitted toCourseAssessmentSubmitted(ExamCandidate candidate,
                                                                ExamAttempt attempt) {
    return new CourseAssessmentSubmitted(
      candidate.getOrganizationId(),
      attempt.getAssessmentExecutionId(),
      attempt.getExamAttemptId(),
      candidate.getExamCandidateId(),
      attempt.getUserId(),
      candidate.getCourseEnrollmentId(),
      candidate.getCourseOfferingId(),
      candidate.getCourseItemId(),
      attempt.getSourceType(),
      attempt.getAttemptNo(),
      attempt.getSubmittedTime() == null ? LocalDateTime.now() : attempt.getSubmittedTime());
  }

  private void markCourseResultEffective(ExamAttempt attempt) {
    LocalDateTime now = LocalDateTime.now();
    attempt.setAttemptStatus("RESULT_EFFECTIVE");
    attempt.setReviewCompletedTime(now);
    attempt.setResultEffectiveTime(now);
    attempt.setIsPassed(attempt.getFinalScore().compareTo(attempt.getPassScoreSnapshot()) >= 0 ? 1 : 0);
  }

  /**
   * 培训正式考试没有待人工批阅题时，客观题判分结果即为正式成绩。
   */
  private void markTrainingExamScorePublished(ExamAttempt attempt) {
    attempt.setAttemptStatus("PUBLISHED");
    attempt.setReviewCompletedTime(LocalDateTime.now());
    attempt.setScorePublishedTime(LocalDateTime.now());
    attempt.setIsPassed(attempt.getFinalScore().compareTo(attempt.getPassScoreSnapshot()) >= 0 ? 1 : 0);
  }

  private void updateCandidateAfterEffective(ExamCandidate candidate, ExamAttempt attempt) {
    candidate.setCandidateStatus(attempt.getIsPassed() != null && attempt.getIsPassed() == 1 ? "PASSED" : "FAILED");
    candidate.setFinalScore(attempt.getFinalScore());
    if (candidate.getBestScore() == null || attempt.getFinalScore().compareTo(candidate.getBestScore()) > 0) {
      candidate.setBestScore(attempt.getFinalScore());
    }
    candidate.setFinalAttemptId(attempt.getExamAttemptId());
    candidate.setResultEffectiveTime(attempt.getResultEffectiveTime());
    notifyStandaloneCertificateIssue(candidate);
  }

  private void autoGrade(ExamAttempt attempt) {
    List<ExamAttemptQuestion> questions = attemptQuestionMapper.selectList(
      new LambdaQueryWrapper<ExamAttemptQuestion>()
        .eq(ExamAttemptQuestion::getExamAttemptId, attempt.getExamAttemptId()));
    for (ExamAttemptQuestion aq : questions) {
      ExamAnswer answer = examAnswerMapper.selectOne(new LambdaQueryWrapper<ExamAnswer>()
        .eq(ExamAnswer::getExamAttemptId, attempt.getExamAttemptId())
        .eq(ExamAnswer::getAttemptQuestionId, aq.getAttemptQuestionId()));
      if (answer == null) {
        answer = new ExamAnswer();
        answer.setExamAttemptId(attempt.getExamAttemptId());
        answer.setAttemptQuestionId(aq.getAttemptQuestionId());
        answer.setIsAnswered(0);
        answer.setFinalScore(BigDecimal.ZERO);
        answer.setGradingStatus("UNANSWERED");
        examAnswerMapper.insert(answer);
      }
      if ("MANUAL".equals(aq.getGradingMode()) || "ESSAY".equals(aq.getQuestionType())) {
        answer.setGradingStatus("PENDING_MANUAL");
        examAnswerMapper.updateById(answer);
        continue;
      }
      GradeResult result = gradeObjective(aq, answer, attempt.getMultiSelectScoringSnapshot());
      answer.setAutoScore(result.score());
      answer.setFinalScore(result.score());
      answer.setIsCorrect(result.correct() ? 1 : 0);
      answer.setGradingStatus("AUTO_GRADED");
      examAnswerMapper.updateById(answer);
    }
  }

  private GradeResult gradeObjective(ExamAttemptQuestion aq, ExamAnswer answer, String multiSelectScoring) {
    // 新版不评分练习的题目分值为空；判对错仍复用算法，但不得让分值计算产生空指针。
    BigDecimal questionScore = aq.getScore() == null ? BigDecimal.ZERO : aq.getScore();
    List<ExamAnswerItem> items = examAnswerItemMapper.selectList(new LambdaQueryWrapper<ExamAnswerItem>()
      .eq(ExamAnswerItem::getExamAnswerId, answer.getExamAnswerId()));
    if ("FILL".equals(aq.getQuestionType())) {
      return gradeFill(aq, items, answer.getAnswerText());
    }
    List<ExamAttemptOption> options = attemptOptionMapper.selectList(new LambdaQueryWrapper<ExamAttemptOption>()
      .eq(ExamAttemptOption::getAttemptQuestionId, aq.getAttemptQuestionId()));
    Set<String> correctKeys = options.stream()
      .filter(o -> o.getIsCorrectSnapshot() != null && o.getIsCorrectSnapshot() == 1)
      .map(ExamAttemptOption::getOptionKey)
      .collect(Collectors.toSet());
    Set<String> selected = items.stream()
      .filter(i -> "OPTION".equals(i.getItemType()))
      .map(ExamAnswerItem::getItemKey)
      .collect(Collectors.toSet());
    if (selected.isEmpty() && StringUtils.hasText(answer.getAnswerText())) {
      selected = Arrays.stream(answer.getAnswerText().split(","))
        .map(String::trim).filter(StringUtils::hasText).collect(Collectors.toSet());
    }
    if ("MULTIPLE".equals(aq.getQuestionType()) && "PARTIAL".equals(multiSelectScoring)) {
      if (selected.isEmpty()) {
        return new GradeResult(BigDecimal.ZERO, false);
      }
      long wrong = selected.stream().filter(k -> !correctKeys.contains(k)).count();
      if (wrong > 0) {
        return new GradeResult(BigDecimal.ZERO, false);
      }
      if (selected.equals(correctKeys)) {
        return new GradeResult(questionScore, true);
      }
      BigDecimal partial = questionScore
        .multiply(BigDecimal.valueOf(selected.size()))
        .divide(BigDecimal.valueOf(Math.max(correctKeys.size(), 1)), 2, java.math.RoundingMode.HALF_UP);
      return new GradeResult(partial, false);
    }
    boolean ok = selected.equals(correctKeys) && !selected.isEmpty();
    return new GradeResult(ok ? questionScore : BigDecimal.ZERO, ok);
  }

  private GradeResult gradeFill(ExamAttemptQuestion aq, List<ExamAnswerItem> items, String answerText) {
    BigDecimal questionScore = aq.getScore() == null ? BigDecimal.ZERO : aq.getScore();
    Map<String, Object> snapshot;
    try {
      snapshot = objectMapper.readValue(
        aq.getCorrectAnswerSnapshotJson() == null ? "{}" : aq.getCorrectAnswerSnapshotJson(),
        Map.class);
    } catch (JsonProcessingException e) {
      return new GradeResult(BigDecimal.ZERO, false);
    }
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> blanks = (List<Map<String, Object>>) snapshot.getOrDefault("blanks", List.of());
    if (blanks.isEmpty()) {
      boolean ok = StringUtils.hasText(answerText)
        && answerText.equals(String.valueOf(snapshot.getOrDefault("standardAnswer", "")));
      return new GradeResult(ok ? questionScore : BigDecimal.ZERO, ok);
    }
    Map<String, String> given = items.stream()
      .filter(i -> "BLANK".equals(i.getItemType()))
      .collect(Collectors.toMap(ExamAnswerItem::getItemKey, ExamAnswerItem::getItemValue, (a, b) -> a));
    boolean all = com.sts.question.service.ObjectiveAnswerEvaluator.correct(
      "FILL", aq.getCorrectAnswerSnapshotJson(), Set.of(), given, answerText);
    return new GradeResult(all ? questionScore : BigDecimal.ZERO, all);
  }

  private void recomputeAttemptScores(ExamAttempt attempt) {
    List<ExamAnswer> answers = examAnswerMapper.selectList(new LambdaQueryWrapper<ExamAnswer>()
      .eq(ExamAnswer::getExamAttemptId, attempt.getExamAttemptId()));
    BigDecimal objective = BigDecimal.ZERO;
    BigDecimal subjective = BigDecimal.ZERO;
    int correct = 0;
    int wrong = 0;
    for (ExamAnswer answer : answers) {
      ExamAttemptQuestion aq = attemptQuestionMapper.selectById(answer.getAttemptQuestionId());
      BigDecimal score = answer.getFinalScore() == null ? BigDecimal.ZERO : answer.getFinalScore();
      if (aq != null && ("MANUAL".equals(aq.getGradingMode()) || "ESSAY".equals(aq.getQuestionType()))) {
        subjective = subjective.add(score);
      } else {
        objective = objective.add(score);
      }
      if (answer.getIsCorrect() != null && answer.getIsCorrect() == 1) {
        correct++;
      } else if (answer.getIsAnswered() != null && answer.getIsAnswered() == 1
        && "AUTO_GRADED".equals(answer.getGradingStatus())) {
        wrong++;
      }
    }
    attempt.setObjectiveScore(objective);
    attempt.setSubjectiveScore(subjective);
    attempt.setFinalScore(objective.add(subjective));
    attempt.setCorrectCount(correct);
    attempt.setWrongCount(wrong);
    examAttemptMapper.updateById(attempt);
  }

  private boolean hasPendingManual(Long attemptId) {
    Long count = examAnswerMapper.selectCount(new LambdaQueryWrapper<ExamAnswer>()
      .eq(ExamAnswer::getExamAttemptId, attemptId)
      .eq(ExamAnswer::getGradingStatus, "PENDING_MANUAL"));
    return count != null && count > 0;
  }

  /**
   * 仅补结算旧版本遗留的纯客观题培训考试，不触碰已有人工批阅记录的答卷。
   */
  private boolean promoteLegacyObjectiveTrainingExam(ExamAttempt attempt, ExamCandidate candidate, Exam exam) {
    if (!"TRAINING_EXAM".equals(attempt.getSourceType())
      || !"PENDING_PUBLISH".equals(attempt.getAttemptStatus())
      || attempt.getFinalScore() == null || attempt.getPassScoreSnapshot() == null
      || hasPendingManual(attempt.getExamAttemptId()) || hasManuallyGradedAnswer(attempt.getExamAttemptId())) {
      return false;
    }
    markTrainingExamScorePublished(attempt);
    examAttemptMapper.updateById(attempt);
    updateCandidateAfterPublish(candidate, attempt);
    notifyTrainingProjection(candidate, exam);
    return true;
  }

  /**
   * 已出现人工评分记录的答卷必须保留教师发布成绩的流程。
   */
  private boolean hasManuallyGradedAnswer(Long attemptId) {
    Long count = examAnswerMapper.selectCount(new LambdaQueryWrapper<ExamAnswer>()
      .eq(ExamAnswer::getExamAttemptId, attemptId)
      .eq(ExamAnswer::getGradingStatus, "MANUAL_GRADED"));
    return count != null && count > 0;
  }

  private void updateCandidateAfterPublish(ExamCandidate candidate, ExamAttempt attempt) {
    ExamPolicy policy = examPolicyMapper.selectOne(new LambdaQueryWrapper<ExamPolicy>()
                                                     .eq(ExamPolicy::getExamId,
                                                         examIdForExecution(candidate.getAssessmentExecutionId())));
    String mode = policy == null || policy.getRetakeScoreMode() == null
      ? "HIGHEST" : policy.getRetakeScoreMode();
    if (candidate.getBestScore() == null
      || attempt.getFinalScore().compareTo(candidate.getBestScore()) > 0) {
      candidate.setBestScore(attempt.getFinalScore());
    }
    if ("LATEST".equals(mode) || candidate.getFinalScore() == null
      || attempt.getFinalScore().compareTo(candidate.getFinalScore()) >= 0) {
      candidate.setFinalScore(attempt.getFinalScore());
      candidate.setFinalAttemptId(attempt.getExamAttemptId());
    }
    if ("HIGHEST".equals(mode)) {
      candidate.setFinalScore(candidate.getBestScore());
    }
    candidate.setResultPublishedTime(LocalDateTime.now());
    BigDecimal finalScore = candidate.getFinalScore();
    Integer passed = finalScore != null && attempt.getPassScoreSnapshot() != null
      ? finalScore.compareTo(attempt.getPassScoreSnapshot()) >= 0 ? 1 : 0 : null;
    candidate.setCandidateStatus(passed == null ? "COMPLETED" : passed == 1 ? "PASSED" : "FAILED");
    examCandidateMapper.updateById(candidate);
    notifyStandaloneCertificateIssue(candidate);
  }

  private void freezeQuestions(ExamAttempt attempt, Long paperVersionId) {
    List<PaperVersionQuestion> pqs = paperVersionQuestionMapper.selectList(
      new LambdaQueryWrapper<PaperVersionQuestion>()
        .eq(PaperVersionQuestion::getPaperVersionId, paperVersionId)
        .orderByAsc(PaperVersionQuestion::getSortOrder));
    for (PaperVersionQuestion pq : pqs) {
      QuestionVersion qv = questionVersionMapper.selectById(pq.getQuestionVersionId());
      if (qv == null) {
        throw new BusinessException(HttpStatus.BAD_REQUEST, 7023, "试卷题目版本缺失");
      }
      ExamAttemptQuestion aq = new ExamAttemptQuestion();
      aq.setExamAttemptId(attempt.getExamAttemptId());
      aq.setQuestionId(pq.getQuestionId());
      aq.setQuestionVersionId(pq.getQuestionVersionId());
      aq.setQuestionType(pq.getQuestionType());
      aq.setStemSnapshot(qv.getStem());
      aq.setAnalysisSnapshot(qv.getAnalysis());
      aq.setGradingMode(qv.getGradingMode());
      aq.setScore(pq.getScore());
      aq.setSortOrder(pq.getSortOrder());
      aq.setSourceBankId(pq.getSourceBankId());
      aq.setCorrectAnswerSnapshotJson(buildCorrectSnapshot(qv));
      aq.setKnowledgePointIdsSnapshotJson(knowledgeSnapshotService.snapshot(attempt.getAssessmentExecutionId(),pq.getQuestionId(),pq.getQuestionVersionId()));
      attemptQuestionMapper.insert(aq);

      List<QuestionOption> options = questionOptionMapper.selectList(new LambdaQueryWrapper<QuestionOption>()
        .eq(QuestionOption::getQuestionVersionId, qv.getQuestionVersionId())
        .orderByAsc(QuestionOption::getSortOrder));
      for (QuestionOption opt : options) {
        ExamAttemptOption ao = new ExamAttemptOption();
        ao.setAttemptQuestionId(aq.getAttemptQuestionId());
        ao.setOptionKey(opt.getOptionKey());
        ao.setOptionContentSnapshot(opt.getOptionContent());
        ao.setIsCorrectSnapshot(opt.getIsCorrect());
        ao.setSortOrder(opt.getSortOrder());
        attemptOptionMapper.insert(ao);
      }
    }
  }

  private void freezeQuestionBank(ExamAttempt attempt, AssessmentExecution execution) {
    Long questionBankId = execution.getResourceId();
    String questionPoolHash = policyText(execution.getPolicySnapshotJson(), "questionPoolHash");
    List<com.sts.question.draw.QuestionDrawEngine.Candidate> commonPool =
      "TRAINING_PRACTICE".equals(execution.getSourceType()) && StringUtils.hasText(questionPoolHash)
        ? executionQuestionPoolService.loadCandidates(execution.getAssessmentExecutionId(), questionPoolHash)
        : candidatePoolService.loadCandidates(questionBankId);
    Map<Long, QuestionVersion> versions = new LinkedHashMap<>();
    List<QuestionBankDrawEngine.Candidate> pool = new ArrayList<>();
    for (com.sts.question.draw.QuestionDrawEngine.Candidate candidate : commonPool) {
      QuestionVersion version = questionVersionMapper.selectById(candidate.questionVersionId());
      if (version == null) {
        continue;
      }
      versions.put(candidate.questionId(), version);
      pool.add(new QuestionBankDrawEngine.Candidate(
        candidate.questionId(), candidate.questionVersionId(), candidate.questionType(),
        candidate.difficulty(), candidate.knowledgePointIds(), candidate.evaluationMode()));
    }
    List<QuestionBankDrawEngine.Rule> rules =
      QuestionBankDrawEngine.parseRules(execution.getPolicySnapshotJson(), objectMapper);
    if ("TRAINING_PRACTICE".equals(execution.getSourceType()) && rules.isEmpty()) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 7063, "培训题库练习缺少有效冻结抽题规则");
    }
    if (rules.isEmpty()) {
      int order = 0;
      for (QuestionBankDrawEngine.Candidate candidate : pool) {
        QuestionVersion version = versions.get(candidate.questionId());
        if (version == null) {
          continue;
        }
        persistBankQuestion(attempt, candidate.questionId(), version, questionBankId,
                            BigDecimal.ONE, order++, null, null, candidate.evaluationMode());
      }
      if (order == 0) {
        throw new BusinessException(HttpStatus.BAD_REQUEST, 7063, "题库没有可用的已发布题目");
      }
      attempt.setTotalScoreSnapshot(BigDecimal.valueOf(order));
      examAttemptMapper.updateById(attempt);
      return;
    }
    long seed = ThreadLocalRandom.current().nextLong();
    attempt.setDrawSeed(seed);
    QuestionBankDrawEngine.DrawResult drawn = QuestionBankDrawEngine.draw(pool, rules, seed);
    if (attempt.getPassScoreSnapshot() != null
      && attempt.getPassScoreSnapshot().compareTo(drawn.totalScore()) > 0) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 7063, "及格线超过抽题总分，练习无法通过");
    }
    for (QuestionBankDrawEngine.DrawnQuestion item : drawn.questions()) {
      QuestionVersion version = versions.get(item.questionId());
      persistBankQuestion(attempt, item.questionId(), version, questionBankId,
                          item.score(), item.sortOrder() - 1, seed, item.ruleIndex(), item.evaluationMode());
    }
    attempt.setTotalScoreSnapshot(isUnscoredQuestionBankPractice(execution) ? null : drawn.totalScore());
    examAttemptMapper.updateById(attempt);
  }

  private void persistBankQuestion(ExamAttempt attempt, Long questionId, QuestionVersion version,
                                   Long questionBankId, BigDecimal score, int sortOrder, Long drawSeed,
                                   Integer drawRuleIndex,
                                   com.sts.question.draw.QuestionDrawEngine.EvaluationMode evaluationMode) {
    ExamAttemptQuestion aq = new ExamAttemptQuestion();
    aq.setExamAttemptId(attempt.getExamAttemptId());
    aq.setQuestionId(questionId);
    aq.setQuestionVersionId(version.getQuestionVersionId());
    aq.setQuestionType(version.getQuestionType());
    aq.setStemSnapshot(version.getStem());
    aq.setAnalysisSnapshot(version.getAnalysis());
    aq.setGradingMode(version.getGradingMode());
    // 题库练习不设及格线，但客观题仍按每题 1 分形成可核对的自动得分；简答题继续只记录。
    aq.setScore(isUnscoredQuestionBankPractice(attempt) && evaluationMode != null
      ? (evaluationMode == com.sts.question.draw.QuestionDrawEngine.EvaluationMode.AUTO
        ? (score == null || score.signum() == 0 ? BigDecimal.ONE : score) : null) : score);
    aq.setSortOrder(sortOrder);
    aq.setSourceBankId(questionBankId);
    aq.setDrawRuleIndex(drawRuleIndex);
    aq.setEvaluationMode(evaluationMode == null ? null : evaluationMode.name());
    aq.setCorrectAnswerSnapshotJson(buildCorrectSnapshot(version, drawSeed));
    aq.setKnowledgePointIdsSnapshotJson(knowledgeSnapshotService.snapshot(attempt.getAssessmentExecutionId(),questionId,version.getQuestionVersionId()));
    attemptQuestionMapper.insert(aq);
    List<QuestionOption> options = questionOptionMapper.selectList(new LambdaQueryWrapper<QuestionOption>()
                                                                     .eq(QuestionOption::getQuestionVersionId,
                                                                         version.getQuestionVersionId())
                                                                     .orderByAsc(QuestionOption::getSortOrder));
    for (QuestionOption option : options) {
      ExamAttemptOption snapshot = new ExamAttemptOption();
      snapshot.setAttemptQuestionId(aq.getAttemptQuestionId());
      snapshot.setOptionKey(option.getOptionKey());
      snapshot.setOptionContentSnapshot(option.getOptionContent());
      snapshot.setIsCorrectSnapshot(option.getIsCorrect());
      snapshot.setSortOrder(option.getSortOrder());
      attemptOptionMapper.insert(snapshot);
    }
  }

  private String policyText(String json, String field) {
    if (!StringUtils.hasText(json)) {
      return null;
    }
    try {
      JsonNode value = objectMapper.readTree(json).get(field);
      return value == null || value.isNull() ? null : value.asText();
    } catch (Exception ex) {
      throw new BusinessException(HttpStatus.CONFLICT, 8167, "考核执行冻结策略无法解析");
    }
  }

  /** 普通测验的零标记不能复用次数参数的最小值为一逻辑。 */
  private boolean isFinalCourseAssessment(AssessmentExecution execution) {
    if (!"COURSE_QUIZ".equals(execution.getSourceType()) || !StringUtils.hasText(execution.getPolicySnapshotJson())) return false;
    try { return objectMapper.readTree(execution.getPolicySnapshotJson()).path("isFinalAssessment").asInt(0) == 1; }
    catch (JsonProcessingException ex) { throw new BusinessException(HttpStatus.BAD_REQUEST, 7061, "结业执行规则无效"); }
  }

  private int policyInt(String json, String field, int fallback) {
    if (!StringUtils.hasText(json)) {
      return fallback;
    }
    try {
      JsonNode value = objectMapper.readTree(json).get(field);
      return value == null || !value.canConvertToInt() ? fallback : Math.max(1, value.asInt());
    } catch (Exception ex) {
      return fallback;
    }
  }

  /** 装配个人练习与正式考试共用的服务端答案快照，不向作答端直接公开。 */
  public String buildCorrectSnapshot(QuestionVersion qv) {
    return buildCorrectSnapshot(qv, null);
  }

  private String buildCorrectSnapshot(QuestionVersion qv, Long drawSeed) {
    try {
      Map<String, Object> map = new LinkedHashMap<>();
      map.put("standardAnswer", qv.getStandardAnswer());
      List<QuestionBlankAnswer> blanks = blankAnswerMapper.selectList(new LambdaQueryWrapper<QuestionBlankAnswer>()
        .eq(QuestionBlankAnswer::getQuestionVersionId, qv.getQuestionVersionId())
        .orderByAsc(QuestionBlankAnswer::getBlankNo)
        .orderByAsc(QuestionBlankAnswer::getAnswerGroupNo));
      Map<Integer, List<String>> byBlank = new LinkedHashMap<>();
      for (QuestionBlankAnswer blank : blanks) {
        byBlank.computeIfAbsent(blank.getBlankNo(), k -> new ArrayList<>()).add(blank.getAnswerText());
      }
      List<Map<String, Object>> blankList = new ArrayList<>();
      byBlank.forEach((no, answers) -> {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("blankNo", no);
        b.put("answers", answers);
        blankList.add(b);
      });
      map.put("blanks", blankList);
      List<QuestionOption> options = questionOptionMapper.selectList(new LambdaQueryWrapper<QuestionOption>()
        .eq(QuestionOption::getQuestionVersionId, qv.getQuestionVersionId()));
      map.put("correctOptions", options.stream()
        .filter(o -> o.getIsCorrect() != null && o.getIsCorrect() == 1)
        .map(QuestionOption::getOptionKey).toList());
      if (drawSeed != null) {
        map.put("drawSeed", drawSeed);
      }
      return objectMapper.writeValueAsString(map);
    } catch (JsonProcessingException e) {
      return "{}";
    }
  }

  /**
   * 补考不得新建考生，必须落在既有考试人员上。
   */
  private ExamCandidate requireExistingCandidate(AssessmentExecution execution, StartAttemptRequest request) {
    ExamCandidate candidate = findCandidate(execution, request);
    if (candidate == null) {
      throw new BusinessException(HttpStatus.NOT_FOUND, 7011, "考试人员不存在");
    }
    return candidate;
  }

  /**
   * 创建新补考尝试前按策略校验开关、考试状态、窗口、范围和次数。
   */
  private void assertCanCreateRetake(Exam exam, ExamCandidate candidate) {
    RetakeEligibilityDecision decision = evaluateRetakeEligibilityDecision(exam, candidate);
    if (decision.eligibility().getEligible()) {
      return;
    }
    throw new BusinessException(HttpStatus.BAD_REQUEST, decision.firstErrorCode(), decision.eligibility().getReasons().getFirst());
  }

  /**
   * 正式发布前由服务端按当前总分和冻结及格分计算预计结果；待阅主观题不提供预计结论。
   */
  private Integer predictIsPassed(ExamAttempt attempt) {
    if (attempt.getFinalScore() == null || attempt.getPassScoreSnapshot() == null || hasPendingManual(attempt.getExamAttemptId())) {
      return null;
    }
    return attempt.getFinalScore().compareTo(attempt.getPassScoreSnapshot()) >= 0 ? 1 : 0;
  }

  /**
   * 读取和创建补考复用同一策略判定，避免页面提示与最终写入护栏不一致。
   */
  private ExamRetakeEligibilityVO evaluateRetakeEligibility(Exam exam, ExamCandidate candidate) {
    return evaluateRetakeEligibilityDecision(exam, candidate).eligibility();
  }

  /**
   * 收集全部不满足条件，首个条件沿用既有开补考接口的业务错误码和校验顺序。
   */
  private RetakeEligibilityDecision evaluateRetakeEligibilityDecision(Exam exam, ExamCandidate candidate) {
    List<String> reasons = new ArrayList<>();
    List<Integer> errorCodes = new ArrayList<>();
    ExamRetakeEligibilityVO eligibility = new ExamRetakeEligibilityVO();
    int max = 0;
    int used = 0;
    if (exam == null) {
      reasons.add("考试不存在");
      errorCodes.add(7001);
    }
    ExamPolicy policy = examPolicyMapper.selectOne(new LambdaQueryWrapper<ExamPolicy>()
                                                     .eq(ExamPolicy::getExamId, exam == null ? null : exam.getExamId()));
    boolean retakeEnabled = policy != null && policy.getIsRetakeEnabled() != null && policy.getIsRetakeEnabled() == 1;
    if (!retakeEnabled) {
      reasons.add("未启用补考");
      errorCodes.add(7051);
    }
    if (exam != null && !List.of("PUBLISHED", "RUNNING", "ENDED").contains(exam.getPublishStatus())) {
      reasons.add("考试状态不允许补考");
      errorCodes.add(7056);
    }
    LocalDateTime now = LocalDateTime.now();
    if (retakeEnabled && policy.getRetakeStartTime() != null && now.isBefore(policy.getRetakeStartTime())) {
      reasons.add("不在补考开放时间内");
      errorCodes.add(7054);
    }
    if (retakeEnabled && policy.getRetakeEndTime() != null && now.isAfter(policy.getRetakeEndTime())) {
      reasons.add("不在补考开放时间内");
      errorCodes.add(7054);
    }
    if (candidate == null || (retakeEnabled && !matchesRetakeScope(candidate, policy.getRetakeScope()))) {
      reasons.add(candidate == null ? "考试人员不存在" : "当前考生不在补考范围");
      errorCodes.add(candidate == null ? 7011 : 7055);
    }
    if (retakeEnabled) {
      max = policy.getRetakeMaxCount() == null ? 0 : policy.getRetakeMaxCount();
      if (candidate != null) {
        Long count = examAttemptMapper.selectCount(new LambdaQueryWrapper<ExamAttempt>()
                                                     .eq(ExamAttempt::getExamCandidateId, candidate.getExamCandidateId())
                                                     .eq(ExamAttempt::getAttemptType, "RETAKE")
                                                     .ne(ExamAttempt::getAttemptStatus, "VOID"));
        used = count == null ? 0 : count.intValue();
      }
      if (used >= max) {
        reasons.add("已超过补考次数");
        errorCodes.add(7052);
      }
      if (exam == null || exam.getPaperVersionId() == null || paperVersionMapper.selectById(exam.getPaperVersionId()) == null) {
        reasons.add("当前考试的冻结试卷不可用");
        errorCodes.add(7005);
      } else if (hasMissingFrozenQuestionVersion(exam.getPaperVersionId())) {
        reasons.add("当前考试的冻结试卷题目版本缺失");
        errorCodes.add(7023);
      }
    }
    eligibility.setEligible(reasons.isEmpty());
    eligibility.setReasons(reasons);
    eligibility.setUsedRetakeCount(used);
    eligibility.setMaxRetakeCount(max);
    return new RetakeEligibilityDecision(eligibility, errorCodes.isEmpty() ? 0 : errorCodes.getFirst());
  }

  /** 补考资格及其首个错误码，仅在服务内部复用。 */
  private record RetakeEligibilityDecision(ExamRetakeEligibilityVO eligibility, int firstErrorCode) {
  }

  private boolean matchesRetakeScope(ExamCandidate candidate, String retakeScope) {
    String scope = StringUtils.hasText(retakeScope) ? retakeScope : "FAILED";
    boolean publishedFailed = "FAILED".equals(candidate.getCandidateStatus())
      && candidate.getResultPublishedTime() != null;
    boolean absent = "ABSENT".equals(candidate.getCandidateStatus());
    return switch (scope) {
      case "ABSENT" -> absent;
      case "FAILED_OR_ABSENT" -> publishedFailed || absent;
      default -> publishedFailed;
    };
  }

  /**
   * 冻结前与 freezeQuestions 使用同一试卷题目版本前置条件，避免资格提示通过后才在创建中失败。
   */
  private boolean hasMissingFrozenQuestionVersion(Long paperVersionId) {
    List<PaperVersionQuestion> paperQuestions = paperVersionQuestionMapper.selectList(
      new LambdaQueryWrapper<PaperVersionQuestion>()
        .eq(PaperVersionQuestion::getPaperVersionId, paperVersionId)
        .orderByAsc(PaperVersionQuestion::getSortOrder));
    return paperQuestions.stream().anyMatch(question -> questionVersionMapper.selectById(question.getQuestionVersionId()) == null);
  }

  private ExamCandidate findCandidate(AssessmentExecution execution, StartAttemptRequest request) {
    return findCandidate(execution, request, false);
  }

  /** 唯一键竞争后用当前读复用刚提交的考生，避免RR快照仍为空。 */
  private ExamCandidate findCandidate(AssessmentExecution execution, StartAttemptRequest request, boolean current) {
    return examCandidateMapper.selectOne(new LambdaQueryWrapper<ExamCandidate>()
                                           .eq(ExamCandidate::getAssessmentExecutionId,
                                               execution.getAssessmentExecutionId())
      .eq(ExamCandidate::getUserId, request.getUserId())
                                           .eq(execution.getCourseOfferingId() != null,
                                               ExamCandidate::getCourseEnrollmentId,
                                               request.getCourseEnrollmentId())
                                           .eq(execution.getCourseOfferingId() != null,
                                               ExamCandidate::getCourseItemId,
                                               request.getCourseItemId()).last(current, "FOR UPDATE"));
  }

  private ExamCandidate findOrCreateCandidate(AssessmentExecution execution, StartAttemptRequest request) {
    ExamCandidate candidate = findCandidate(execution, request);
    if (candidate != null) {
      repairTrainingCandidateBinding(candidate, request);
      return candidate;
    }
    candidate = new ExamCandidate();
    candidate.setAssessmentExecutionId(execution.getAssessmentExecutionId());
    candidate.setUserId(request.getUserId());
    candidate.setCourseOfferingId(0L);
    candidate.setCourseItemId(0L);
    candidate.setCourseEnrollmentId(0L);
    if (execution.getCourseOfferingId() != null) {
      candidate.setCourseOfferingId(request.getCourseOfferingId());
      candidate.setCourseItemId(request.getCourseItemId());
      candidate.setCourseEnrollmentId(request.getCourseEnrollmentId());
      candidate.setAssignmentSource("COURSE");
      candidate.setSourceBusinessId(request.getCourseEnrollmentId());
    } else if (request.getTrainingParticipantId() != null) {
      candidate.setTrainingParticipantId(request.getTrainingParticipantId());
      candidate.setAssignmentSource("TRAINING");
      candidate.setSourceBusinessId(request.getTrainingParticipantId());
    }
    if (candidate.getAssignmentSource() == null) {
      candidate.setAssignmentSource("LINK");
      candidate.setSourceBusinessId(0L);
    }
    candidate.setCandidateProfileSnapshotJson(
      profileSnapshotAssembler.assemble(request.getUserId(), execution.getOrganizationId()));
    candidate.setProfileSnapshotTime(LocalDateTime.now());
    candidate.setCandidateStatus("NOT_STARTED");
    candidate.setAttemptCount(0);
    try {
      examCandidateMapper.insert(candidate);
      return candidate;
    } catch (DuplicateKeyException ex) {
      ExamCandidate concurrent = findCandidate(execution, request, isFinalCourseAssessment(execution));
      if (concurrent != null) {
        return concurrent;
      }
      throw ex;
    }
  }

  private Exam examForExecution(Long assessmentExecutionId) {
    Long examId = examIdForExecution(assessmentExecutionId);
    return examId == null ? null : examMapper.selectById(examId);
  }

  /**
   * 兼容历史未归属候选人，避免培训答卷提交后无法回写培训内容投影。
   */
  private void repairTrainingCandidateBinding(ExamCandidate candidate, StartAttemptRequest request) {
    Long trainingParticipantId = request.getTrainingParticipantId();
    if (trainingParticipantId == null) {
      return;
    }
    String assignmentSource = candidate.getAssignmentSource();
    Long boundParticipantId = candidate.getTrainingParticipantId();
    boolean sourceCanBind = assignmentSource == null || "LINK".equals(assignmentSource)
      || "TRAINING".equals(assignmentSource);
    boolean participantCanBind = boundParticipantId == null
      || Objects.equals(boundParticipantId, trainingParticipantId);
    if (!sourceCanBind || !participantCanBind) {
      throw new BusinessException(HttpStatus.CONFLICT, 8147, "已有考试人员与当前培训参训人不一致");
    }
    if (Objects.equals(boundParticipantId, trainingParticipantId) && "TRAINING".equals(assignmentSource)) {
      return;
    }
    candidate.setTrainingParticipantId(trainingParticipantId);
    candidate.setAssignmentSource("TRAINING");
    examCandidateMapper.updateById(candidate);
  }

  private void notifyTrainingProjection(ExamCandidate candidate, Exam exam) {
    if (candidate == null || candidate.getTrainingParticipantId() == null) {
      return;
    }
    Long planContentId = exam == null ? null : exam.getPlanContentId();
    if (planContentId == null) {
      planContentId = candidate.getSourceBusinessId();
    }
    if (planContentId == null || planContentId == 0L) {
      return;
    }
    eventPublisher.publishEvent(new TrainingParticipantContentFactChangedEvent(
      this, candidate.getTrainingParticipantId(), planContentId));
  }

  /**
   * 独立考试（非培训派生、非课程考核）的考生结论落定后，以考试主数据标识触发证书规则。
   *
   * <p>与 {@link #notifyTrainingProjection} 互为镜像：该方法只处理培训考生并把事实投影给培训域，
   * 本方法只处理独立指派考生并按规则签发理论考试证书。培训考试归属于培训结果，课程考核归属于课程，
   * 都不在此处触发。
   */
  private void notifyStandaloneCertificateIssue(ExamCandidate candidate) {
    if (candidate == null || !"ASSIGN".equals(candidate.getAssignmentSource())) {
      return;
    }
    if (!"PASSED".equals(candidate.getCandidateStatus())) {
      return;
    }
    Long examId = examIdForExecution(candidate.getAssessmentExecutionId());
    if (examId == null) {
      return;
    }
    Exam exam = examMapper.selectById(examId);
    if (exam == null || exam.getPlanContentId() != null) {
      return;
    }
    certificateAutoIssueService.issueForSource("EXAM", exam.getExamId(), candidate.getUserId(),
                                               candidate.getExamCandidateId());
  }

  private Long examIdForExecution(Long assessmentExecutionId) {
    return assessmentExecutionService.requireById(assessmentExecutionId).getExamId();
  }

  private void softDeleteAnswerItems(Long examAnswerId) {
    List<ExamAnswerItem> items = examAnswerItemMapper.selectList(new LambdaQueryWrapper<ExamAnswerItem>()
      .eq(ExamAnswerItem::getExamAnswerId, examAnswerId));
    for (ExamAnswerItem item : items) {
      examAnswerItemMapper.update(null, com.baomidou.mybatisplus.core.toolkit.Wrappers.<ExamAnswerItem>update()
        .setSql("deleted = " + item.getAnswerItemId())
        .eq("answer_item_id", item.getAnswerItemId())
        .eq("deleted", 0L));
    }
  }

  private boolean isTerminal(String status) {
    return List.of("SUBMITTED", "PENDING_REVIEW", "PENDING_PUBLISH", "PUBLISHED", "RESULT_EFFECTIVE", "VOID")
      .contains(status);
  }

  /** 服务端以绝对截止时间裁决提交，不能依赖浏览器定时器仍在运行。 */
  private boolean isDeadlineReached(ExamAttempt attempt) {
    return attempt.getDeadlineTime() != null && !LocalDateTime.now().isBefore(attempt.getDeadlineTime());
  }

  /**
   * 可回看的最新有效终态；作废卷不占用首次作答。
   */
  private ExamAttempt findLatestReusableTerminal(Long examCandidateId, boolean lock) {
    return examAttemptMapper.selectOne(new LambdaQueryWrapper<ExamAttempt>()
                                         .eq(ExamAttempt::getExamCandidateId, examCandidateId)
                                         .notIn(ExamAttempt::getAttemptStatus, List.of("CREATED", "ANSWERING", "VOID"))
                                         .orderByDesc(ExamAttempt::getAttemptNo)
                                         .orderByDesc(ExamAttempt::getExamAttemptId)
                                         .last(lock ? "LIMIT 1 FOR UPDATE" : "LIMIT 1"));
  }

  private boolean isRepeatablePractice(AssessmentExecution execution) {
    return "COURSE_BANK_PRACTICE".equals(execution.getSourceType())
      || "STANDALONE_PRACTICE".equals(execution.getSourceType())
      || "TRAINING_PRACTICE".equals(execution.getSourceType());
  }

  private String derivedAttemptType(boolean retake, AssessmentExecution execution) {
    if (retake) {
      return "RETAKE";
    }
    return isRepeatablePractice(execution) ? "PRACTICE" : "FIRST";
  }

  private void assertAnswering(ExamAttempt attempt) {
    if (!"ANSWERING".equals(attempt.getAttemptStatus()) && !"CREATED".equals(attempt.getAttemptStatus())) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 7021, "当前尝试不可作答");
    }
  }

  private void assertAllQuestionsAnswered(Long examAttemptId) {
    Long questionCount = attemptQuestionMapper.selectCount(new LambdaQueryWrapper<ExamAttemptQuestion>()
                                                             .eq(ExamAttemptQuestion::getExamAttemptId, examAttemptId));
    Long answeredCount = examAnswerMapper.selectCount(new LambdaQueryWrapper<ExamAnswer>()
                                                        .eq(ExamAnswer::getExamAttemptId, examAttemptId)
                                                        .eq(ExamAnswer::getIsAnswered, 1));
    if (!Objects.equals(questionCount, answeredCount)) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 7064, "题库练习需完成全部题目后提交");
    }
  }

  private Exam requireExam(Long id) {
    Exam exam = examMapper.selectById(id);
    if (exam == null) {
      throw new BusinessException(HttpStatus.NOT_FOUND, 7001, "考试不存在");
    }
    return exam;
  }

  private Exam requirePublishedExam(Long id) {
    Exam exam = requireExam(id);
    if (!List.of("PUBLISHED", "RUNNING").contains(exam.getPublishStatus())) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 7006, "考试未发布");
    }
    return exam;
  }

  private PaperVersion requirePaperVersion(Long id) {
    if (id == null) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 7005, "考试未绑定试卷版本");
    }
    PaperVersion pv = paperVersionMapper.selectById(id);
    if (pv == null) {
      throw new BusinessException(HttpStatus.NOT_FOUND, 6503, "试卷版本不存在");
    }
    return pv;
  }

  private ExamAttempt requireAttempt(Long id) {
    ExamAttempt attempt = examAttemptMapper.selectById(id);
    if (attempt == null) {
      throw new BusinessException(HttpStatus.NOT_FOUND, 7020, "作答尝试不存在");
    }
    return attempt;
  }

  private ExamAttemptVO toVO(ExamAttempt attempt, boolean withQuestions) {
    ExamAttemptVO vo = new ExamAttemptVO();
    BeanUtils.copyProperties(attempt, vo);
    AssessmentExecution execution = assessmentExecutionService.requireById(attempt.getAssessmentExecutionId());
    vo.setExamId(execution.getExamId());
    vo.setResourceType(execution.getResourceType());
    vo.setResourceId(execution.getResourceId());
    vo.setResourceName(execution.getResourceNameSnapshot());
    boolean published = "PUBLISHED".equals(attempt.getAttemptStatus())
      && attempt.getScorePublishedTime() != null;
    boolean effective = "RESULT_EFFECTIVE".equals(attempt.getAttemptStatus())
      && attempt.getResultEffectiveTime() != null;
    vo.setScorePublishStatus(effective ? "EFFECTIVE" : published ? "PUBLISHED" : "UNPUBLISHED");
    vo.setScoreVisible(effective || published);
    if (withQuestions) {
      Map<Long, ExamAnswerVO> answers = answersByQuestion(attempt.getExamAttemptId());
      List<ExamAttemptQuestion> questions = attemptQuestionMapper.selectList(
        new LambdaQueryWrapper<ExamAttemptQuestion>()
          .eq(ExamAttemptQuestion::getExamAttemptId, attempt.getExamAttemptId())
          .orderByAsc(ExamAttemptQuestion::getSortOrder));
      vo.setQuestions(questions.stream().map(q -> {
        ExamAttemptQuestionVO qv = new ExamAttemptQuestionVO();
        BeanUtils.copyProperties(q, qv);
        qv.setBlankCount(resolveBlankCount(q));
        qv.setStandardAnswer(extractStandardAnswer(q.getCorrectAnswerSnapshotJson()));
        qv.setOptions(attemptOptionMapper.selectList(new LambdaQueryWrapper<ExamAttemptOption>()
            .eq(ExamAttemptOption::getAttemptQuestionId, q.getAttemptQuestionId())
            .orderByAsc(ExamAttemptOption::getSortOrder))
          .stream().map(o -> {
            ExamAttemptOptionVO ov = new ExamAttemptOptionVO();
            BeanUtils.copyProperties(o, ov);
            return ov;
          }).toList());
        qv.setAnswer(answers.get(q.getAttemptQuestionId()));
        return qv;
      }).toList());
    }
    return vo;
  }

  private Map<Long, ExamAnswerVO> answersByQuestion(Long attemptId) {
    List<ExamAnswer> answers = examAnswerMapper.selectList(new LambdaQueryWrapper<ExamAnswer>()
                                                             .eq(ExamAnswer::getExamAttemptId, attemptId));
    if (answers.isEmpty()) {
      return Map.of();
    }
    Map<Long, List<ExamAnswerItemVO>> items = examAnswerItemMapper.selectList(
        new LambdaQueryWrapper<ExamAnswerItem>()
          .in(ExamAnswerItem::getExamAnswerId,
              answers.stream().map(ExamAnswer::getExamAnswerId).toList())
          .orderByAsc(ExamAnswerItem::getSortOrder))
      .stream().map(item -> {
        ExamAnswerItemVO itemVO = new ExamAnswerItemVO();
        BeanUtils.copyProperties(item, itemVO);
        return Map.entry(item.getExamAnswerId(), itemVO);
      }).collect(Collectors.groupingBy(Map.Entry::getKey,
                                       Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
    /** 每题只取最近一次人工批阅意见，供管理端作答详情回显。 */
    Map<Long, String> reviewComments = reviewRecordMapper.selectList(new LambdaQueryWrapper<ExamReviewRecord>()
        .in(ExamReviewRecord::getExamAnswerId, answers.stream().map(ExamAnswer::getExamAnswerId).toList())
        .orderByDesc(ExamReviewRecord::getReviewedTime)
        .orderByDesc(ExamReviewRecord::getReviewRecordId))
      .stream()
      .collect(Collectors.toMap(ExamReviewRecord::getExamAnswerId, ExamReviewRecord::getReviewComment, (latest, ignored) -> latest));
    return answers.stream().map(answer -> {
      ExamAnswerVO answerVO = new ExamAnswerVO();
      BeanUtils.copyProperties(answer, answerVO);
      answerVO.setItems(items.getOrDefault(answer.getExamAnswerId(), List.of()));
      answerVO.setReviewComment(reviewComments.get(answer.getExamAnswerId()));
      return answerVO;
    }).collect(Collectors.toMap(ExamAnswerVO::getAttemptQuestionId, Function.identity()));
  }

  private String extractStandardAnswer(String correctAnswerSnapshotJson) {
    if (!StringUtils.hasText(correctAnswerSnapshotJson)) {
      return null;
    }
    try {
      JsonNode value = objectMapper.readTree(correctAnswerSnapshotJson).get("standardAnswer");
      return value == null || value.isNull() ? null : value.asText();
    } catch (Exception ignored) {
      return null;
    }
  }

  /** 从冻结快照派生填空数量，管理端与学员端使用相同的空位编号口径。 */
  private Integer resolveBlankCount(ExamAttemptQuestion question) {
    if (!"FILL".equals(question.getQuestionType())) return null;
    try {
      JsonNode blanks = objectMapper.readTree(question.getCorrectAnswerSnapshotJson()).path("blanks");
      return blanks.isArray() && !blanks.isEmpty() ? blanks.size() : 1;
    } catch (Exception ignored) {
      return 1;
    }
  }

  private record GradeResult(BigDecimal score, boolean correct) {
  }
}

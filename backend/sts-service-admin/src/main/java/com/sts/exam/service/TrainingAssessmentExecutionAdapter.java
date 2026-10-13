package com.sts.exam.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sts.common.exception.BusinessException;
import com.sts.exam.dto.StartAttemptRequest;
import com.sts.exam.entity.*;
import com.sts.exam.mapper.*;
import com.sts.exam.vo.ExamAttemptVO;
import com.sts.training.dto.TrainingExamPolicySnapshot;
import com.sts.training.dto.TrainingExamReviewerSnapshot;
import com.sts.training.port.TrainingAssessmentExecutionPort;
import com.sts.training.port.TrainingAssessmentResult;
import com.sts.training.port.TrainingExamExecutionCommand;
import com.sts.training.port.TrainingParticipantBinding;
import com.sts.training.vo.TrainingLearnerExamNoticeVO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 考试域实现培训考核执行内部端口。培训域不得直接写考试 Mapper。
 */
@Service
@RequiredArgsConstructor
public class TrainingAssessmentExecutionAdapter implements TrainingAssessmentExecutionPort {

  private final ExamMapper examMapper;
  private final ExamPolicyMapper examPolicyMapper;
  private final ExamCandidateMapper examCandidateMapper;
  private final ExamAttemptMapper examAttemptMapper;
  private final ExamReviewerMapper examReviewerMapper;
  private final AssessmentExecutionService assessmentExecutionService;
  private final ExamService examService;
  private final ExamAttemptService examAttemptService;
  private final ExamCandidateProfileSnapshotAssembler profileSnapshotAssembler;
  private final AssessmentExecutionQuestionPoolService questionPoolService;

  @Override
  @Transactional
  public Long ensureExamExecution(TrainingExamExecutionCommand command) {
    Exam existing = findByPlanContent(command.getPlanContentId());
    if (existing != null) {
      if ("DRAFT".equals(existing.getPublishStatus())) {
        Map<String, Object> extra = extraPolicy(command);
        extra.put("assessmentKind", command.getAssessmentKind());
        examService.publish(existing.getExamId(), extra);
      }
      AssessmentExecution execution = assessmentExecutionService.findStandaloneByExamId(existing.getExamId());
      if (execution == null) {
        throw new BusinessException(HttpStatus.CONFLICT, 8142,
                                    "计划内容 " + command.getPlanContentId() + " 既有考试缺少考核执行");
      }
      assertReusable(existing, execution, command);
      ensureQuestionPool(execution.getAssessmentExecutionId(), command);
      return execution.getAssessmentExecutionId();
    }
    Exam exam = "QUESTION_BANK".equals(command.getContentType())
      ? createBankPracticeExam(command) : createPlanExamFromSnapshot(command);
    try {
      examMapper.insert(exam);
    } catch (DuplicateKeyException ex) {
      Exam concurrent = findByPlanContent(command.getPlanContentId());
      if (concurrent == null) {
        throw ex;
      }
      AssessmentExecution execution = assessmentExecutionService.requireStandaloneByExamId(concurrent.getExamId());
      assertReusable(concurrent, execution, command);
      ensureQuestionPool(execution.getAssessmentExecutionId(), command);
      return execution.getAssessmentExecutionId();
    }
    insertPolicy(exam.getExamId(), command);
    Map<String, Object> extra = extraPolicy(command);
    extra.put("assessmentKind", command.getAssessmentKind());
    examService.publish(exam.getExamId(), extra);
    AssessmentExecution execution = assessmentExecutionService.requireStandaloneByExamId(exam.getExamId());
    insertReviewers(execution.getAssessmentExecutionId(), command);
    ensureQuestionPool(execution.getAssessmentExecutionId(), command);
    return execution.getAssessmentExecutionId();
  }

  @Override
  @Transactional
  public void ensureCandidates(Long assessmentExecutionId, Long planContentId,
                               List<TrainingParticipantBinding> participants) {
    if (participants == null || participants.isEmpty()) {
      return;
    }
    AssessmentExecution execution = assessmentExecutionService.requireById(assessmentExecutionId);
    LocalDateTime now = LocalDateTime.now();
    for (TrainingParticipantBinding binding : participants) {
      ExamCandidate existing = examCandidateMapper.selectOne(
        new LambdaQueryWrapper<ExamCandidate>()
          .eq(ExamCandidate::getAssessmentExecutionId, assessmentExecutionId)
          .eq(ExamCandidate::getUserId, binding.getUserId())
          .eq(ExamCandidate::getCourseEnrollmentId, 0L)
          .eq(ExamCandidate::getCourseItemId, 0L));
      if (existing != null) {
        assertTrainingCandidate(existing, binding, planContentId);
        continue;
      }
      ExamCandidate candidate = new ExamCandidate();
      candidate.setAssessmentExecutionId(assessmentExecutionId);
      candidate.setUserId(binding.getUserId());
      candidate.setCourseOfferingId(0L);
      candidate.setCourseItemId(0L);
      candidate.setCourseEnrollmentId(0L);
      candidate.setTrainingParticipantId(binding.getTrainingParticipantId());
      candidate.setAssignmentSource("TRAINING");
      candidate.setSourceBusinessId(planContentId);
      candidate.setCandidateProfileSnapshotJson(
        profileSnapshotAssembler.assemble(binding.getUserId(), execution.getOrganizationId()));
      candidate.setProfileSnapshotTime(now);
      candidate.setCandidateStatus("NOT_STARTED");
      candidate.setAttemptCount(0);
      try {
        examCandidateMapper.insert(candidate);
      } catch (DuplicateKeyException ex) {
        ExamCandidate concurrent = examCandidateMapper.selectOne(
          new LambdaQueryWrapper<ExamCandidate>()
            .eq(ExamCandidate::getAssessmentExecutionId, assessmentExecutionId)
            .eq(ExamCandidate::getUserId, binding.getUserId())
            .eq(ExamCandidate::getCourseEnrollmentId, 0L)
            .eq(ExamCandidate::getCourseItemId, 0L));
        if (concurrent == null) {
          throw ex;
        }
        assertTrainingCandidate(concurrent, binding, planContentId);
      }
    }
  }

  @Override
  public ExamAttemptVO startAttempt(Long assessmentExecutionId, Long trainingParticipantId,
                                    Long currentUserId, String clientAttemptToken,
                                    String platform, String operatingSystem,
                                    String browser, String clientIp) {
    AssessmentExecution execution = assessmentExecutionService.requireById(assessmentExecutionId);
    StartAttemptRequest start = new StartAttemptRequest();
    start.setAssessmentExecutionId(assessmentExecutionId);
    start.setExamId(execution.getExamId());
    start.setUserId(currentUserId);
    start.setTrainingParticipantId(trainingParticipantId);
    start.setClientAttemptToken(clientAttemptToken);
    start.setPlatform(platform);
    start.setOperatingSystem(operatingSystem);
    start.setBrowser(browser);
    start.setClientIp(clientIp);
    return examAttemptService.start(start);
  }

  @Override
  public TrainingLearnerExamNoticeVO getLearnerExamNotice(Long assessmentExecutionId) {
    AssessmentExecution execution = assessmentExecutionService.requireById(assessmentExecutionId);
    Exam exam = execution.getExamId() == null ? null : examMapper.selectById(execution.getExamId());
    if (exam == null) {
      throw new BusinessException(HttpStatus.CONFLICT, 8152, "考试执行实例缺少计划专属考试");
    }
    TrainingLearnerExamNoticeVO notice = new TrainingLearnerExamNoticeVO();
    notice.setExamId(exam.getExamId());
    notice.setExamName(exam.getExamName());
    notice.setExamType(exam.getExamType());
    notice.setOpenStartTime(exam.getOpenStartTime());
    notice.setOpenEndTime(exam.getOpenEndTime());
    notice.setDurationSeconds(exam.getDurationSeconds());
    notice.setPassRatio(exam.getPassRatio());
    notice.setPassScore(exam.getPassScore());
    notice.setIsNoticeEnabled(exam.getIsNoticeEnabled());
    notice.setNoticeContent(exam.getNoticeContent());
    return notice;
  }

  @Override
  public TrainingAssessmentResult readResult(Long assessmentExecutionId, Long trainingParticipantId) {
    ExamCandidate candidate = examCandidateMapper.selectOne(
      new LambdaQueryWrapper<ExamCandidate>()
        .eq(ExamCandidate::getAssessmentExecutionId, assessmentExecutionId)
        .eq(ExamCandidate::getTrainingParticipantId, trainingParticipantId)
        .eq(ExamCandidate::getAssignmentSource, "TRAINING")
        .last("LIMIT 1"));
    if (candidate == null) {
      return null;
    }
    TrainingAssessmentResult result = new TrainingAssessmentResult();
    result.setAssessmentExecutionId(assessmentExecutionId);
    result.setSourceRecordId(candidate.getExamCandidateId());
    result.setSourceStatus(candidate.getCandidateStatus());
    result.setFinalAttemptId(candidate.getFinalAttemptId());
    if (candidate.getFinalAttemptId() != null) {
      ExamAttempt finalAttempt = examAttemptMapper.selectById(candidate.getFinalAttemptId());
      if (finalAttempt != null && "TRAINING_PRACTICE".equals(finalAttempt.getSourceType())) {
        result.setEvaluatedCount(finalAttempt.getEvaluatedCount());
        result.setCorrectCount(finalAttempt.getCorrectCount());
        result.setWrongCount(finalAttempt.getWrongCount());
        result.setUnassessedCount(finalAttempt.getUnassessedCount());
        result.setAccuracyRate(finalAttempt.getAccuracyRate());
      }
    }
    if ("COMPLETED".equals(candidate.getCandidateStatus())) {
      result.setCompletedTime(candidate.getResultEffectiveTime());
    } else if (candidate.getResultPublishedTime() != null) {
      result.setScore(candidate.getFinalScore());
      result.setCompletedTime(candidate.getResultPublishedTime());
    }
    return result;
  }

  @Override
  public boolean existsCandidateForParticipant(Long trainingParticipantId) {
    return examCandidateMapper.selectCount(new LambdaQueryWrapper<ExamCandidate>()
                                             .eq(ExamCandidate::getTrainingParticipantId,
                                                 trainingParticipantId)) > 0;
  }

  @Override
  public List<Long> listInProgressAttemptParticipantIds(Long assessmentExecutionId) {
    List<ExamAttempt> attempts = examAttemptMapper.selectList(
      new LambdaQueryWrapper<ExamAttempt>()
        .eq(ExamAttempt::getAssessmentExecutionId, assessmentExecutionId)
        .eq(ExamAttempt::getAttemptStatus, "IN_PROGRESS"));
    if (attempts.isEmpty()) {
      return List.of();
    }
    List<Long> candidateIds = attempts.stream().map(ExamAttempt::getExamCandidateId).distinct().toList();
    return examCandidateMapper.selectList(new LambdaQueryWrapper<ExamCandidate>()
                                            .in(ExamCandidate::getExamCandidateId, candidateIds))
      .stream()
      .map(ExamCandidate::getTrainingParticipantId)
      .filter(Objects::nonNull)
      .distinct()
      .toList();
  }

  private Exam createPlanExamFromSnapshot(TrainingExamExecutionCommand command) {
    if (command.getPaperVersionId() == null || command.getDurationSeconds() == null
      || command.getPassScore() == null || command.getQuestionOrderMode() == null
      || command.getMultiSelectScoring() == null) {
      throw new BusinessException(HttpStatus.CONFLICT, 8142,
                                  "计划内容 " + command.getPlanContentId() + " 发布快照缺少考试运行参数");
    }
    Exam exam = new Exam();
    exam.setOrganizationId(command.getOrganizationId());
    exam.setExamCode("TRN-E-" + command.getPlanContentId());
    exam.setExamName(command.getExamName());
    exam.setExamType("ONLINE");
    exam.setExamCategoryId(command.getExamCategoryId());
    exam.setCoverFileId(command.getCoverFileId());
    exam.setPaperVersionId(command.getPaperVersionId());
    exam.setPlanContentId(command.getPlanContentId());
    exam.setResponsibleUserId(command.getResponsibleUserId());
    exam.setOpenStartTime(command.getOpenStartTime());
    exam.setOpenEndTime(command.getOpenEndTime());
    exam.setDurationSeconds(command.getDurationSeconds());
    exam.setPassRatio(command.getPassRatio());
    exam.setPassScore(command.getPassScore());
    exam.setCredit(command.getCredit() == null ? BigDecimal.ZERO : command.getCredit());
    exam.setQuestionOrderMode(command.getQuestionOrderMode());
    exam.setMultiSelectScoring(command.getMultiSelectScoring());
    exam.setIsNoticeEnabled(command.getIsNoticeEnabled() == null ? 0 : command.getIsNoticeEnabled());
    exam.setNoticeContent(command.getNoticeContent());
    exam.setPublishStatus("DRAFT");
    exam.setStatus(1);
    return exam;
  }

  private Exam createBankPracticeExam(TrainingExamExecutionCommand command) {
    if (command.getQuestionBankId() == null) {
      throw new BusinessException(HttpStatus.CONFLICT, 8148, "题库练习未绑定题库");
    }
    BigDecimal total = BigDecimal.ZERO;
    if (command.getDrawRules() != null) {
      for (var rule : command.getDrawRules()) {
        if (rule != null && rule.getDrawCount() != null && rule.getScorePerQuestion() != null) {
          total = total.add(rule.getScorePerQuestion().multiply(BigDecimal.valueOf(rule.getDrawCount())));
        }
      }
    }
    if (command.getPassScore() != null && total.compareTo(BigDecimal.ZERO) > 0
      && command.getPassScore().compareTo(total) > 0) {
      throw new BusinessException(HttpStatus.CONFLICT, 8148, "题库练习及格线超过抽题总分");
    }
    Exam exam = new Exam();
    exam.setOrganizationId(command.getOrganizationId());
    exam.setExamCode("TRN-Q-" + command.getPlanContentId());
    exam.setExamName(command.getExamName());
    exam.setExamType("BANK_PRACTICE");
    exam.setQuestionBankId(command.getQuestionBankId());
    exam.setPlanContentId(command.getPlanContentId());
    exam.setOpenStartTime(command.getOpenStartTime());
    exam.setOpenEndTime(command.getOpenEndTime());
    exam.setDurationSeconds(command.getDurationSeconds() == null ? 0 : command.getDurationSeconds());
    exam.setPassRatio(new BigDecimal("0.6000"));
    exam.setPassScore(command.getPassScore() == null ? BigDecimal.ZERO : command.getPassScore());
    exam.setCredit(BigDecimal.ZERO);
    exam.setQuestionOrderMode("RANDOM");
    exam.setMultiSelectScoring("ALL_OR_NOTHING");
    exam.setIsNoticeEnabled(0);
    exam.setPublishStatus("DRAFT");
    exam.setStatus(1);
    return exam;
  }

  private void insertPolicy(Long examId, TrainingExamExecutionCommand command) {
    ExamPolicy policy = new ExamPolicy();
    policy.setExamId(examId);
    TrainingExamPolicySnapshot snap = command.getPolicy();
    if (snap != null) {
      policy.setIsAnticheatEnabled(nz(snap.getIsAnticheatEnabled(), 0));
      policy.setIsWatermarkEnabled(nz(snap.getIsWatermarkEnabled(), 0));
      policy.setIsPcAllowed(nz(snap.getIsPcAllowed(), 1));
      policy.setIsAppAllowed(nz(snap.getIsAppAllowed(), 1));
      policy.setIsH5Allowed(nz(snap.getIsH5Allowed(), 1));
      policy.setSwitchLimit(snap.getSwitchLimit());
      policy.setIsSplitScreenForceSubmit(nz(snap.getIsSplitScreenForceSubmit(), 0));
      policy.setIsCopyRefreshDevtoolDisabled(nz(snap.getIsCopyRefreshDevtoolDisabled(), 0));
      policy.setInactivitySeconds(snap.getInactivitySeconds());
      policy.setInactivityCountdownSeconds(snap.getInactivityCountdownSeconds());
      policy.setMinimumAnswerSeconds(snap.getMinimumAnswerSeconds());
      int maxAttempts = command.getMaxAttempts() == null ? 1 : command.getMaxAttempts();
      policy.setIsRetakeEnabled(snap.getIsRetakeEnabled() == null
                                  ? (maxAttempts > 1 ? 1 : 0) : snap.getIsRetakeEnabled());
      policy.setRetakeScope(snap.getRetakeScope());
      policy.setRetakeStartTime(parseRetakeTime(snap.getRetakeStartTime(), command.getPlanContentId()));
      policy.setRetakeEndTime(parseRetakeTime(snap.getRetakeEndTime(), command.getPlanContentId()));
      policy.setRetakeMaxCount(snap.getRetakeMaxCount() == null
                                 ? Math.max(0, maxAttempts - 1) : snap.getRetakeMaxCount());
      policy.setRetakeScoreMode(snap.getRetakeScoreMode() == null ? "HIGHEST" : snap.getRetakeScoreMode());
      policy.setIsRetakeRecordVisible(nz(snap.getIsRetakeRecordVisible(), 1));
      policy.setPassPoint(snap.getPassPoint() == null ? BigDecimal.ZERO : snap.getPassPoint());
      policy.setFailPoint(snap.getFailPoint() == null ? BigDecimal.ZERO : snap.getFailPoint());
      policy.setAbsentPoint(snap.getAbsentPoint() == null ? BigDecimal.ZERO : snap.getAbsentPoint());
      policy.setLateForbidSeconds(nz(snap.getLateForbidSeconds(), 0));
      policy.setAnswerDetailMode(snap.getAnswerDetailMode() == null ? "SUBMITTED" : snap.getAnswerDetailMode());
      policy.setIsAnalysisVisible(nz(snap.getIsAnalysisVisible(), 1));
      policy.setIsScoreVisible(nz(snap.getIsScoreVisible(), 1));
      policy.setIsRankingEnabled(nz(snap.getIsRankingEnabled(), 0));
    } else {
      policy.setIsAnticheatEnabled(0);
      policy.setIsWatermarkEnabled(0);
      policy.setIsPcAllowed(1);
      policy.setIsAppAllowed(1);
      policy.setIsH5Allowed(1);
      policy.setIsSplitScreenForceSubmit(0);
      policy.setIsCopyRefreshDevtoolDisabled(0);
      int maxAttempts = command.getMaxAttempts() == null ? 1 : command.getMaxAttempts();
      policy.setIsRetakeEnabled(maxAttempts > 1 ? 1 : 0);
      policy.setRetakeMaxCount(Math.max(0, maxAttempts - 1));
      policy.setRetakeScoreMode("HIGHEST");
      policy.setIsRetakeRecordVisible(1);
      policy.setPassPoint(BigDecimal.ZERO);
      policy.setFailPoint(BigDecimal.ZERO);
      policy.setAbsentPoint(BigDecimal.ZERO);
      policy.setLateForbidSeconds(0);
      policy.setAnswerDetailMode(command.getAnswerDetailMode() == null ? "SUBMITTED" : command.getAnswerDetailMode());
      policy.setIsAnalysisVisible(command.getIsAnalysisVisible() == null ? 1 : command.getIsAnalysisVisible());
      policy.setIsScoreVisible(command.getIsScoreVisible() == null ? 1 : command.getIsScoreVisible());
      policy.setIsRankingEnabled(0);
    }
    examPolicyMapper.insert(policy);
  }

  private void insertReviewers(Long assessmentExecutionId, TrainingExamExecutionCommand command) {
    if (command.getReviewers() == null) {
      return;
    }
    for (TrainingExamReviewerSnapshot reviewer : command.getReviewers()) {
      if (reviewer == null || reviewer.getReviewerUserId() == null) {
        continue;
      }
      ExamReviewer row = new ExamReviewer();
      row.setAssessmentExecutionId(assessmentExecutionId);
      row.setReviewerUserId(reviewer.getReviewerUserId());
      row.setAssignmentSource(reviewer.getAssignmentSource() == null ? "USER" : reviewer.getAssignmentSource());
      examReviewerMapper.insert(row);
    }
  }

  private Map<String, Object> extraPolicy(TrainingExamExecutionCommand command) {
    Map<String, Object> extra = new LinkedHashMap<>();
    extra.put("trainingPlanContentId", command.getPlanContentId());
    extra.put("trainingResourceSnapshotHash", command.getResourceSnapshotHash());
    if ("QUESTION_BANK".equals(command.getContentType())) {
      extra.put("maxAttempts", command.getMaxAttempts());
      extra.put("drawRules", command.getDrawRules());
      extra.put("questionPoolHash", command.getQuestionPoolHash());
    }
    return extra;
  }

  private void assertReusable(Exam existing, AssessmentExecution execution, TrainingExamExecutionCommand command) {
    Long fingerprintContentId = fingerprintLong(execution, "trainingPlanContentId");
    String fingerprintHash = fingerprintText(execution, "trainingResourceSnapshotHash");
    if (!Objects.equals(command.getPlanContentId(), fingerprintContentId)
      || !Objects.equals(command.getResourceSnapshotHash(), fingerprintHash)
      || (!"QUESTION_BANK".equals(command.getContentType())
      && !reviewerSetMatches(execution.getAssessmentExecutionId(), command.getReviewers()))) {
      throw new BusinessException(HttpStatus.CONFLICT, 8142,
                                  "计划内容 " + command.getPlanContentId() + " 既有执行与发布快照不一致");
    }
  }

  private void ensureQuestionPool(Long executionId, TrainingExamExecutionCommand command) {
    if (!"QUESTION_BANK".equals(command.getContentType())) {
      return;
    }
    if (command.getQuestionPoolHash() == null || command.getQuestionPool() == null
      || command.getQuestionPool().isEmpty()) {
      throw new BusinessException(HttpStatus.CONFLICT, 8167, "题库练习缺少发布冻结题池");
    }
    questionPoolService.ensure(executionId, command.getPlanContentId(), command.getQuestionBankId(),
                               command.getQuestionPoolHash(), command.getQuestionPool());
  }

  private boolean reviewerSetMatches(Long assessmentExecutionId,
                                     List<TrainingExamReviewerSnapshot> expected) {
    List<ExamReviewer> actual = examReviewerMapper.selectList(
      new LambdaQueryWrapper<ExamReviewer>()
        .eq(ExamReviewer::getAssessmentExecutionId, assessmentExecutionId));
    List<String> actualKeys = actual.stream()
      .map(row -> row.getReviewerUserId() + ":" + (row.getAssignmentSource() == null ? "USER" : row.getAssignmentSource()))
      .sorted()
      .toList();
    List<String> expectedKeys = (expected == null ? List.<TrainingExamReviewerSnapshot>of() : expected).stream()
      .filter(row -> row != null && row.getReviewerUserId() != null)
      .map(row -> row.getReviewerUserId() + ":" + (row.getAssignmentSource() == null ? "USER" : row.getAssignmentSource()))
      .sorted()
      .toList();
    return actualKeys.equals(expectedKeys);
  }

  private Long fingerprintLong(AssessmentExecution execution, String key) {
    Object value = fingerprintValue(execution, key);
    if (value == null) {
      return null;
    }
    if (value instanceof Number number) {
      return number.longValue();
    }
    return Long.valueOf(value.toString());
  }

  private String fingerprintText(AssessmentExecution execution, String key) {
    Object value = fingerprintValue(execution, key);
    return value == null ? null : value.toString();
  }

  @SuppressWarnings("unchecked")
  private Object fingerprintValue(AssessmentExecution execution, String key) {
    if (execution == null || execution.getPolicySnapshotJson() == null) {
      return null;
    }
    try {
      Map<String, Object> policy = new com.fasterxml.jackson.databind.ObjectMapper()
        .readValue(execution.getPolicySnapshotJson(), Map.class);
      return policy.get(key);
    } catch (Exception ex) {
      return null;
    }
  }

  private Integer nz(Integer value, int fallback) {
    return value == null ? fallback : value;
  }

  private java.time.LocalDateTime parseRetakeTime(String text, Long planContentId) {
    if (text == null || text.isBlank()) {
      return null;
    }
    try {
      return java.time.LocalDateTime.parse(text);
    } catch (java.time.format.DateTimeParseException ex) {
      throw new BusinessException(HttpStatus.CONFLICT, 8142,
                                  "计划内容 " + planContentId + " 补考时间无法解析");
    }
  }

  private Exam findByPlanContent(Long planContentId) {
    return examMapper.selectOne(new LambdaQueryWrapper<Exam>()
                                  .eq(Exam::getPlanContentId, planContentId)
                                  .last("LIMIT 1"));
  }

  private void assertTrainingCandidate(ExamCandidate existing, TrainingParticipantBinding binding,
                                       Long planContentId) {
    if (!"TRAINING".equals(existing.getAssignmentSource())
      || !Objects.equals(existing.getTrainingParticipantId(), binding.getTrainingParticipantId())
      || !Objects.equals(existing.getSourceBusinessId(), planContentId)) {
      throw new BusinessException(HttpStatus.CONFLICT, 8147, "已有考试人员与当前培训参训人不一致");
    }
  }
}

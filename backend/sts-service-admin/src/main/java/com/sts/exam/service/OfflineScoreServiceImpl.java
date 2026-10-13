package com.sts.exam.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sts.common.exception.BusinessException;
import com.sts.exam.dto.OfflineScoreRecordRequest;
import com.sts.exam.dto.OfflineScoresRequest;
import com.sts.exam.entity.AssessmentExecution;
import com.sts.exam.entity.Exam;
import com.sts.exam.entity.ExamAttempt;
import com.sts.exam.entity.ExamCandidate;
import com.sts.exam.mapper.ExamAttemptMapper;
import com.sts.exam.mapper.ExamCandidateMapper;
import com.sts.exam.mapper.ExamMapper;
import com.sts.exam.vo.OfflineScoreRecordResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 线下考试成绩录入实现。
 * <p>
 * 成绩落到 {@code ExamAttempt}（作为学员端“查看成绩”的读取载体）并同步 {@code ExamCandidate}；
 * 线下考试没有题目快照，因此不写作答题目表。
 */
@Service
@RequiredArgsConstructor
public class OfflineScoreServiceImpl implements OfflineScoreService {

  /** 允许录入成绩的考试发布状态。 */
  private static final List<String> RECORDABLE_STATUS = List.of("PUBLISHED", "RUNNING", "ENDED");
  /** 线下成绩的作答尝试标记，用于幂等定位与来源留痕。 */
  private static final String OFFLINE_SUBMIT_REASON = "OFFLINE_IMPORT";

  private final ExamMapper examMapper;
  private final ExamCandidateMapper candidateMapper;
  private final ExamAttemptMapper attemptMapper;
  private final AssessmentExecutionService assessmentExecutionService;

  @Override
  @Transactional
  public OfflineScoreRecordResult record(Long examId, OfflineScoresRequest request) {
    Exam exam = examMapper.selectById(examId);
    if (exam == null) {
      throw new BusinessException(HttpStatus.NOT_FOUND, 7001, "考试不存在");
    }
    if (!RECORDABLE_STATUS.contains(exam.getPublishStatus())) {
      throw new BusinessException(HttpStatus.CONFLICT, 7006, "考试尚未发布，无法录入成绩");
    }
    if (!"OFFLINE".equals(exam.getExamType())) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 7069, "仅线下考试支持录入成绩");
    }
    AssessmentExecution execution = assessmentExecutionService.requireStandaloneByExamId(examId);
    BigDecimal totalScore = execution.getTotalScoreSnapshot() == null
      ? BigDecimal.valueOf(100) : execution.getTotalScoreSnapshot();
    BigDecimal passScore = execution.getPassScoreSnapshot();
    LocalDateTime now = LocalDateTime.now();

    int recorded = 0;
    for (OfflineScoreRecordRequest item : request.getScores()) {
      ExamCandidate candidate = candidateMapper.selectById(item.getExamCandidateId());
      if (candidate == null
        || !Objects.equals(candidate.getAssessmentExecutionId(), execution.getAssessmentExecutionId())) {
        throw new BusinessException(HttpStatus.BAD_REQUEST, 7065, "考生不属于该考试");
      }
      BigDecimal score = item.getFinalScore();
      if (score == null || score.compareTo(BigDecimal.ZERO) < 0 || score.compareTo(totalScore) > 0) {
        throw new BusinessException(HttpStatus.BAD_REQUEST, 7068,
                                    "成绩必须落在 0 到 " + totalScore.stripTrailingZeros().toPlainString() + " 之间");
      }
      boolean passed = passScore == null || score.compareTo(passScore) >= 0;

      ExamAttempt attempt = upsertAttempt(execution, candidate, score, totalScore, passScore, passed, now,
                                          exam.getOpenStartTime());

      int attemptCount = candidate.getAttemptCount() == null ? 0 : candidate.getAttemptCount();
      candidate.setCandidateStatus(passed ? "PASSED" : "FAILED");
      candidate.setAttemptCount(Math.max(attemptCount, 1));
      candidate.setFinalScore(score);
      candidate.setBestScore(score);
      candidate.setFinalAttemptId(attempt.getExamAttemptId());
      candidate.setResultPublishedTime(now);
      candidate.setResultEffectiveTime(now);
      candidateMapper.updateById(candidate);
      recorded++;
    }
    return new OfflineScoreRecordResult(examId, recorded, request.getScores().size() - recorded);
  }

  /**
   * 幂等写入成绩载体：已存在线下成绩尝试则覆盖，不新增行。
   */
  private ExamAttempt upsertAttempt(AssessmentExecution execution, ExamCandidate candidate,
                                    BigDecimal score, BigDecimal totalScore, BigDecimal passScore,
                                    boolean passed, LocalDateTime now, LocalDateTime sessionStartTime) {
    ExamAttempt attempt = findOfflineAttempt(candidate);
    if (attempt == null) {
      attempt = new ExamAttempt();
      attempt.setOrganizationId(execution.getOrganizationId());
      attempt.setAssessmentExecutionId(execution.getAssessmentExecutionId());
      attempt.setSourceType(execution.getSourceType());
      attempt.setAssessmentKind(execution.getAssessmentKind());
      attempt.setExamCandidateId(candidate.getExamCandidateId());
      attempt.setUserId(candidate.getUserId());
      attempt.setPaperSourceType(execution.getResourceType());
      attempt.setPaperSourceId(execution.getResourceId());
      attempt.setDurationSecondsSnapshot(execution.getDurationSecondsSnapshot());
      attempt.setQuestionOrderModeSnapshot(execution.getQuestionOrderModeSnapshot());
      attempt.setMultiSelectScoringSnapshot(execution.getMultiSelectScoringSnapshot());
      attempt.setAttemptNo(1);
      attempt.setAttemptType("FIRST");
      attempt.setUsedSeconds(0);
      // 线下成绩由管理端产生，没有客户端令牌；用固定令牌保证同一考生只落一条。
      attempt.setClientAttemptToken("offline-" + candidate.getExamCandidateId());
    }
    attempt.setAttemptStatus("PUBLISHED");
    // 线下考试复用考试开放开始时间作为场次开始时间，未配置时保留空值而不伪造在线开考事实。
    attempt.setStartedTime(sessionStartTime);
    attempt.setTotalScoreSnapshot(totalScore);
    attempt.setPassScoreSnapshot(passScore);
    attempt.setObjectiveScore(BigDecimal.ZERO);
    attempt.setSubjectiveScore(BigDecimal.ZERO);
    attempt.setFinalScore(score);
    attempt.setIsPassed(passed ? 1 : 0);
    attempt.setCorrectCount(0);
    attempt.setWrongCount(0);
    attempt.setSubmittedTime(now);
    attempt.setScorePublishedTime(now);
    attempt.setResultEffectiveTime(now);
    attempt.setSubmitReason(OFFLINE_SUBMIT_REASON);
    if (attempt.getExamAttemptId() == null) {
      attemptMapper.insert(attempt);
    } else {
      attemptMapper.updateById(attempt);
    }
    return attempt;
  }

  private ExamAttempt findOfflineAttempt(ExamCandidate candidate) {
    if (candidate.getFinalAttemptId() != null) {
      ExamAttempt bound = attemptMapper.selectById(candidate.getFinalAttemptId());
      if (bound != null && OFFLINE_SUBMIT_REASON.equals(bound.getSubmitReason())) {
        return bound;
      }
    }
    return attemptMapper.selectOne(new LambdaQueryWrapper<ExamAttempt>()
                                     .eq(ExamAttempt::getExamCandidateId, candidate.getExamCandidateId())
                                     .eq(ExamAttempt::getSubmitReason, OFFLINE_SUBMIT_REASON)
                                     .last("LIMIT 1"));
  }
}

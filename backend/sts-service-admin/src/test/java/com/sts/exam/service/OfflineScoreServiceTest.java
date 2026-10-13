package com.sts.exam.service;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 线下成绩录入的边界与幂等：越界、非线下、跨考生拒绝，重复录入覆盖不新增。
 */
class OfflineScoreServiceTest {

  private ExamMapper examMapper;
  private ExamCandidateMapper candidateMapper;
  private ExamAttemptMapper attemptMapper;
  private AssessmentExecutionService executionService;
  private OfflineScoreService service;

  @BeforeEach
  void setUp() {
    examMapper = mock(ExamMapper.class);
    candidateMapper = mock(ExamCandidateMapper.class);
    attemptMapper = mock(ExamAttemptMapper.class);
    executionService = mock(AssessmentExecutionService.class);
    when(executionService.requireStandaloneByExamId(3L)).thenReturn(execution());
    service = new OfflineScoreServiceImpl(examMapper, candidateMapper, attemptMapper, executionService);
  }

  @DisplayName("录分：草稿考试直接拒绝为 7006")
  @Test
  void record_rejectsDraftExam() {
    Exam draft = exam("OFFLINE");
    draft.setPublishStatus("DRAFT");
    when(examMapper.selectById(3L)).thenReturn(draft);

    assertThatThrownBy(() -> service.record(3L, request(12L, "88")))
      .isInstanceOf(BusinessException.class)
      .extracting("code").isEqualTo(7006);
  }

  @DisplayName("录分：非线下考试拒绝为 7069")
  @Test
  void record_rejectsNonOfflineExam() {
    when(examMapper.selectById(3L)).thenReturn(exam("ONLINE"));

    assertThatThrownBy(() -> service.record(3L, request(12L, "88")))
      .isInstanceOf(BusinessException.class)
      .extracting("code").isEqualTo(7069);
  }

  @DisplayName("录分：考生不属于本场考试拒绝为 7065 且不写入")
  @Test
  void record_rejectsCandidateFromOtherExam() {
    when(examMapper.selectById(3L)).thenReturn(exam("OFFLINE"));
    ExamCandidate foreign = candidate(12L);
    foreign.setAssessmentExecutionId(999L);
    when(candidateMapper.selectById(12L)).thenReturn(foreign);

    assertThatThrownBy(() -> service.record(3L, request(12L, "88")))
      .isInstanceOf(BusinessException.class)
      .extracting("code").isEqualTo(7065);
    verify(attemptMapper, never()).insert(any(ExamAttempt.class));
  }

  @DisplayName("录分：分数超出满分拒绝为 7068 且不写入")
  @Test
  void record_rejectsScoreAboveTotalScore() {
    when(examMapper.selectById(3L)).thenReturn(exam("OFFLINE"));
    when(candidateMapper.selectById(12L)).thenReturn(candidate(12L));

    assertThatThrownBy(() -> service.record(3L, request(12L, "101")))
      .isInstanceOf(BusinessException.class)
      .extracting("code").isEqualTo(7068);
    verify(attemptMapper, never()).insert(any(ExamAttempt.class));
  }

  @DisplayName("录分：首次录入写入一条已发布作答并同步考生合格状态")
  @Test
  void record_insertsPublishedAttemptAndMarksCandidatePassed() {
    when(examMapper.selectById(3L)).thenReturn(exam("OFFLINE"));
    ExamCandidate candidate = candidate(12L);
    when(candidateMapper.selectById(12L)).thenReturn(candidate);
    when(attemptMapper.selectOne(any())).thenReturn(null);

    OfflineScoreRecordResult result = service.record(3L, request(12L, "88"));

    assertThat(result.getExamId()).isEqualTo(3L);
    assertThat(result.getRecordedCount()).isEqualTo(1);
    assertThat(result.getSkippedCount()).isZero();
    verify(attemptMapper).insert(any(ExamAttempt.class));
    assertThat(candidate.getCandidateStatus()).isEqualTo("PASSED");
    assertThat(candidate.getFinalScore()).isEqualByComparingTo("88");
    assertThat(candidate.getAttemptCount()).isEqualTo(1);
    var attemptCaptor = forClass(ExamAttempt.class);
    verify(attemptMapper).insert(attemptCaptor.capture());
    assertThat(attemptCaptor.getValue().getStartedTime()).isEqualTo(LocalDateTime.of(2026, 9, 18, 9, 0));
  }

  @DisplayName("录分：分数低于及格线标记为未通过")
  @Test
  void record_marksCandidateFailedBelowPassScore() {
    when(examMapper.selectById(3L)).thenReturn(exam("OFFLINE"));
    ExamCandidate candidate = candidate(12L);
    when(candidateMapper.selectById(12L)).thenReturn(candidate);
    when(attemptMapper.selectOne(any())).thenReturn(null);

    service.record(3L, request(12L, "59"));

    assertThat(candidate.getCandidateStatus()).isEqualTo("FAILED");
  }

  @DisplayName("录分：及格分高于 100 时允许录入达到该及格分的成绩")
  @Test
  void record_acceptsScoreAtHighPassScore() {
    Exam offline = exam("OFFLINE");
    offline.setPassScore(new BigDecimal("120"));
    when(examMapper.selectById(3L)).thenReturn(offline);
    AssessmentExecution highScoreExecution = execution();
    highScoreExecution.setTotalScoreSnapshot(new BigDecimal("120"));
    highScoreExecution.setPassScoreSnapshot(new BigDecimal("120"));
    when(executionService.requireStandaloneByExamId(3L)).thenReturn(highScoreExecution);
    ExamCandidate candidate = candidate(12L);
    when(candidateMapper.selectById(12L)).thenReturn(candidate);
    when(attemptMapper.selectOne(any())).thenReturn(null);

    service.record(3L, request(12L, "120"));

    assertThat(candidate.getCandidateStatus()).isEqualTo("PASSED");
    assertThat(candidate.getFinalScore()).isEqualByComparingTo("120");
  }

  @DisplayName("录分：重复录入覆盖既有作答，不新增 attempt 行")
  @Test
  void record_reusesExistingOfflineAttemptOnRerecord() {
    when(examMapper.selectById(3L)).thenReturn(exam("OFFLINE"));
    ExamCandidate candidate = candidate(12L);
    candidate.setFinalAttemptId(40L);
    candidate.setAttemptCount(1);
    when(candidateMapper.selectById(12L)).thenReturn(candidate);
    ExamAttempt existing = new ExamAttempt();
    existing.setExamAttemptId(40L);
    existing.setExamCandidateId(12L);
    existing.setSubmitReason("OFFLINE_IMPORT");
    when(attemptMapper.selectById(40L)).thenReturn(existing);

    service.record(3L, request(12L, "95"));

    verify(attemptMapper, never()).insert(any(ExamAttempt.class));
    verify(attemptMapper).updateById(existing);
    assertThat(existing.getFinalScore()).isEqualByComparingTo("95");
    assertThat(existing.getAttemptStatus()).isEqualTo("PUBLISHED");
  }

  private OfflineScoresRequest request(Long examCandidateId, String finalScore) {
    OfflineScoreRecordRequest record = new OfflineScoreRecordRequest();
    record.setExamCandidateId(examCandidateId);
    record.setFinalScore(new BigDecimal(finalScore));
    OfflineScoresRequest request = new OfflineScoresRequest();
    request.setScores(List.of(record));
    return request;
  }

  private Exam exam(String examType) {
    Exam value = new Exam();
    value.setExamId(3L);
    value.setExamType(examType);
    value.setPublishStatus("PUBLISHED");
    value.setPassScore(new BigDecimal("60"));
    value.setOpenStartTime(LocalDateTime.of(2026, 9, 18, 9, 0));
    return value;
  }

  private ExamCandidate candidate(Long id) {
    ExamCandidate value = new ExamCandidate();
    value.setExamCandidateId(id);
    value.setAssessmentExecutionId(11L);
    value.setUserId(7L);
    value.setCandidateStatus("PENDING");
    value.setAttemptCount(0);
    return value;
  }

  /** 线下考试执行对象：资源为 OFFLINE_RESULT，默认按百分制冻结满分。 */
  private AssessmentExecution execution() {
    AssessmentExecution value = new AssessmentExecution();
    value.setAssessmentExecutionId(11L);
    value.setExamId(3L);
    value.setSourceType("STANDALONE_EXAM");
    value.setResourceType("OFFLINE_RESULT");
    value.setResourceId(3L);
    value.setTotalScoreSnapshot(new BigDecimal("100"));
    value.setPassScoreSnapshot(new BigDecimal("60"));
    return value;
  }
}

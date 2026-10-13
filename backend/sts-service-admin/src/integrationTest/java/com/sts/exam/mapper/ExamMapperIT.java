package com.sts.exam.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sts.admin.support.AbstractMysqlIntegrationTest;
import com.sts.common.exception.BusinessException;
import com.sts.exam.entity.AssessmentExecution;
import com.sts.exam.entity.Exam;
import com.sts.exam.entity.ExamAttempt;
import com.sts.exam.entity.ExamCandidate;
import com.sts.exam.service.AssessmentExecutionSnapshotCodec;
import com.sts.exam.support.ExamMapperItConfig;
import com.sts.org.mapper.OrganizationMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = ExamMapperItConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class ExamMapperIT extends AbstractMysqlIntegrationTest {

  @Autowired
  private ExamMapper examMapper;
  @Autowired
  private ExamCandidateMapper examCandidateMapper;
  @Autowired
  private ExamAttemptMapper examAttemptMapper;
  @Autowired
  private AssessmentExecutionMapper assessmentExecutionMapper;
  @Autowired
  private OrganizationMapper organizationMapper;
  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Test
  @DisplayName("完整执行快照经过 MySQL JSON 往返后摘要稳定，篡改后拒绝读取")
  void assessmentExecutionSnapshot_survivesMysqlJsonRoundTripAndRejectsTampering() {
    Long orgId = organizationMapper.selectByOrgCode("DEMO").getOrganizationId();
    Long executionId = courseExecution(orgId, 100L, 200L, 101L);
    AssessmentExecution persisted = assessmentExecutionMapper.selectById(executionId);
    AssessmentExecutionSnapshotCodec codec = new AssessmentExecutionSnapshotCodec(new ObjectMapper());

    codec.decodeAndVerify(persisted);

    assertThat(persisted.getResourceNameSnapshot()).isEqualTo("试卷");
    assertThat(persisted.getTotalScoreSnapshot()).isEqualByComparingTo("100");
    jdbcTemplate.update(
      "UPDATE sts_assessment_execution SET snapshot_json = JSON_SET(snapshot_json, '$.resource.id', 999)"
        + " WHERE assessment_execution_id = ?", executionId);
    persisted.setSnapshotJson(jdbcTemplate.queryForObject(
      "SELECT snapshot_json FROM sts_assessment_execution WHERE assessment_execution_id = ?",
      String.class, executionId));
    assertThatThrownBy(() -> codec.decodeAndVerify(persisted))
      .isInstanceOfSatisfying(BusinessException.class,
                              ex -> assertThat(ex.getCode()).isEqualTo(7063));
  }

  @Test
  @DisplayName("考试编码唯一；考生上下文唯一；attempt token 唯一")
  void examCandidateAttemptUniques() {
    Long orgId = organizationMapper.selectByOrgCode("DEMO").getOrganizationId();
    Exam exam = new Exam();
    exam.setOrganizationId(orgId);
    exam.setExamCode("EX-IT-1");
    exam.setExamName("考试");
    exam.setExamType("ONLINE");
    exam.setDurationSeconds(60);
    exam.setPassRatio(new BigDecimal("0.6"));
    exam.setPassScore(new BigDecimal("60"));
    exam.setCredit(BigDecimal.ZERO);
    exam.setQuestionOrderMode("IMPORT");
    exam.setMultiSelectScoring("ALL_OR_NOTHING");
    exam.setIsNoticeEnabled(0);
    exam.setPublishStatus("DRAFT");
    exam.setStatus(1);
    examMapper.insert(exam);

    Exam dup = new Exam();
    dup.setOrganizationId(orgId);
    dup.setExamCode("EX-IT-1");
    dup.setExamName("考试2");
    dup.setExamType("ONLINE");
    dup.setDurationSeconds(60);
    dup.setPassRatio(new BigDecimal("0.6"));
    dup.setPassScore(new BigDecimal("60"));
    dup.setCredit(BigDecimal.ZERO);
    dup.setQuestionOrderMode("IMPORT");
    dup.setMultiSelectScoring("ALL_OR_NOTHING");
    dup.setIsNoticeEnabled(0);
    dup.setPublishStatus("DRAFT");
    dup.setStatus(1);
    assertThatThrownBy(() -> examMapper.insert(dup)).isInstanceOf(DuplicateKeyException.class);

    Exam firstBound = exam(orgId, "EX-PC-1");
    firstBound.setPlanContentId(99001L);
    examMapper.insert(firstBound);
    Exam secondBound = exam(orgId, "EX-PC-2");
    secondBound.setPlanContentId(99001L);
    assertThatThrownBy(() -> examMapper.insert(secondBound)).isInstanceOf(DuplicateKeyException.class);

    ExamCandidate candidate = new ExamCandidate();
    candidate.setOrganizationId(orgId);
    Long executionId = standaloneExecution(orgId, exam.getExamId());
    candidate.setAssessmentExecutionId(executionId);
    candidate.setUserId(100L);
    candidate.setCourseOfferingId(0L);
    candidate.setCourseEnrollmentId(0L);
    candidate.setCourseItemId(0L);
    candidate.setAssignmentSource("LINK");
    candidate.setSourceBusinessId(0L);
    candidate.setCandidateProfileSnapshotJson("{\"userId\":100}");
    candidate.setProfileSnapshotTime(LocalDateTime.now());
    candidate.setCandidateStatus("NOT_STARTED");
    candidate.setAttemptCount(0);
    examCandidateMapper.insert(candidate);
    assertThat(candidate.getExamCandidateId()).isNotNull();

    ExamAttempt attempt = new ExamAttempt();
    attempt.setOrganizationId(orgId);
    attempt.setAssessmentExecutionId(executionId);
    attempt.setSourceType("STANDALONE_EXAM");
    attempt.setAssessmentKind("FORMAL_EXAM");
    attempt.setExamCandidateId(candidate.getExamCandidateId());
    attempt.setUserId(100L);
    attempt.setPaperSourceType("PAPER_VERSION");
    attempt.setPaperSourceId(0L);
    attempt.setTotalScoreSnapshot(new BigDecimal("100"));
    attempt.setPassScoreSnapshot(new BigDecimal("60"));
    attempt.setDurationSecondsSnapshot(60);
    attempt.setQuestionOrderModeSnapshot("IMPORT");
    attempt.setMultiSelectScoringSnapshot("ALL_OR_NOTHING");
    attempt.setAttemptNo(1);
    attempt.setAttemptType("FIRST");
    attempt.setAttemptStatus("ANSWERING");
    attempt.setUsedSeconds(0);
    attempt.setObjectiveScore(BigDecimal.ZERO);
    attempt.setSubjectiveScore(BigDecimal.ZERO);
    attempt.setFinalScore(BigDecimal.ZERO);
    attempt.setCorrectCount(0);
    attempt.setWrongCount(0);
    attempt.setClientAttemptToken("token-it-1");
    examAttemptMapper.insert(attempt);

    ExamAttempt tokenDup = new ExamAttempt();
    tokenDup.setOrganizationId(orgId);
    tokenDup.setAssessmentExecutionId(executionId);
    tokenDup.setSourceType("STANDALONE_EXAM");
    tokenDup.setAssessmentKind("FORMAL_EXAM");
    tokenDup.setExamCandidateId(candidate.getExamCandidateId());
    tokenDup.setUserId(100L);
    tokenDup.setPaperSourceType("PAPER_VERSION");
    tokenDup.setPaperSourceId(0L);
    tokenDup.setTotalScoreSnapshot(new BigDecimal("100"));
    tokenDup.setPassScoreSnapshot(new BigDecimal("60"));
    tokenDup.setDurationSecondsSnapshot(60);
    tokenDup.setQuestionOrderModeSnapshot("IMPORT");
    tokenDup.setMultiSelectScoringSnapshot("ALL_OR_NOTHING");
    tokenDup.setAttemptNo(2);
    tokenDup.setAttemptType("RETAKE");
    tokenDup.setAttemptStatus("ANSWERING");
    tokenDup.setUsedSeconds(0);
    tokenDup.setObjectiveScore(BigDecimal.ZERO);
    tokenDup.setSubjectiveScore(BigDecimal.ZERO);
    tokenDup.setFinalScore(BigDecimal.ZERO);
    tokenDup.setCorrectCount(0);
    tokenDup.setWrongCount(0);
    tokenDup.setClientAttemptToken("token-it-1");
    assertThatThrownBy(() -> examAttemptMapper.insert(tokenDup)).isInstanceOf(DuplicateKeyException.class);
  }

  @Test
  @DisplayName("课程 Candidate：同一报名可关联同一考试的不同内容项，但同一内容项唯一")
  void courseCandidateUniqueByEnrollmentAndItem() {
    Long orgId = organizationMapper.selectByOrgCode("DEMO").getOrganizationId();
    Exam exam = new Exam();
    exam.setOrganizationId(orgId);
    exam.setExamCode("EX-IT-COURSE-1");
    exam.setExamName("课程考试");
    exam.setExamType("ONLINE");
    exam.setDurationSeconds(60);
    exam.setPassRatio(new BigDecimal("0.6"));
    exam.setPassScore(new BigDecimal("60"));
    exam.setCredit(BigDecimal.ZERO);
    exam.setQuestionOrderMode("IMPORT");
    exam.setMultiSelectScoring("ALL_OR_NOTHING");
    exam.setIsNoticeEnabled(0);
    exam.setPublishStatus("DRAFT");
    exam.setStatus(1);
    examMapper.insert(exam);

    Long firstExecution = courseExecution(orgId, 100L, 200L, 101L);
    Long secondExecution = courseExecution(orgId, 100L, 200L, 102L);
    ExamCandidate first = courseCandidate(orgId, firstExecution, 1000L, 101L);
    examCandidateMapper.insert(first);
    ExamCandidate secondItem = courseCandidate(orgId, secondExecution, 1000L, 102L);
    examCandidateMapper.insert(secondItem);
    assertThat(secondItem.getExamCandidateId()).isNotNull();

    assertThatThrownBy(() -> examCandidateMapper.insert(courseCandidate(orgId, firstExecution, 1000L, 101L)))
      .isInstanceOf(DuplicateKeyException.class);
  }

  @Test
  @DisplayName("培训 Candidate：同一考试执行和用户只保留一个候选人")
  void trainingCandidateUniqueByExecutionAndUser() {
    Long orgId = organizationMapper.selectByOrgCode("DEMO").getOrganizationId();
    Exam exam = exam(orgId, "EX-IT-TRAINING-1");
    examMapper.insert(exam);

    Long executionId = standaloneExecution(orgId, exam.getExamId());
    examCandidateMapper.insert(trainingCandidate(orgId, executionId, 100L, 501L, 601L));
    assertThatThrownBy(() -> examCandidateMapper.insert(
      trainingCandidate(orgId, executionId, 100L, 502L, 601L)))
      .isInstanceOf(DuplicateKeyException.class);
  }

  private ExamCandidate courseCandidate(Long orgId, Long executionId, Long enrollmentId, Long itemId) {
    ExamCandidate candidate = new ExamCandidate();
    candidate.setOrganizationId(orgId);
    candidate.setAssessmentExecutionId(executionId);
    candidate.setUserId(100L);
    candidate.setCourseOfferingId(100L);
    candidate.setCourseEnrollmentId(enrollmentId);
    candidate.setCourseItemId(itemId);
    candidate.setAssignmentSource("COURSE");
    candidate.setSourceBusinessId(0L);
    candidate.setCandidateProfileSnapshotJson("{\"userId\":100}");
    candidate.setProfileSnapshotTime(LocalDateTime.now());
    candidate.setCandidateStatus("NOT_STARTED");
    candidate.setAttemptCount(0);
    return candidate;
  }

  private Exam exam(Long orgId, String code) {
    Exam exam = new Exam();
    exam.setOrganizationId(orgId);
    exam.setExamCode(code);
    exam.setExamName("培训考试");
    exam.setExamType("ONLINE");
    exam.setDurationSeconds(60);
    exam.setPassRatio(new BigDecimal("0.6"));
    exam.setPassScore(new BigDecimal("60"));
    exam.setCredit(BigDecimal.ZERO);
    exam.setQuestionOrderMode("IMPORT");
    exam.setMultiSelectScoring("ALL_OR_NOTHING");
    exam.setIsNoticeEnabled(0);
    exam.setPublishStatus("PUBLISHED");
    exam.setStatus(1);
    return exam;
  }

  private ExamCandidate trainingCandidate(Long orgId, Long executionId, Long userId,
                                          Long planContentId, Long participantId) {
    ExamCandidate candidate = new ExamCandidate();
    candidate.setOrganizationId(orgId);
    candidate.setAssessmentExecutionId(executionId);
    candidate.setUserId(userId);
    candidate.setCourseOfferingId(0L);
    candidate.setCourseEnrollmentId(0L);
    candidate.setCourseItemId(0L);
    candidate.setTrainingParticipantId(participantId);
    candidate.setAssignmentSource("TRAINING");
    candidate.setSourceBusinessId(participantId);
    candidate.setCandidateProfileSnapshotJson("{\"userId\":" + userId + "}");
    candidate.setProfileSnapshotTime(LocalDateTime.now());
    candidate.setCandidateStatus("NOT_STARTED");
    candidate.setAttemptCount(0);
    return candidate;
  }

  private Long standaloneExecution(Long orgId, Long examId) {
    return execution(orgId, "STANDALONE_EXAM", examId, examId, null, null, null);
  }

  private Long courseExecution(Long orgId, Long offeringId, Long versionId, Long itemId) {
    return execution(orgId, "COURSE_QUIZ", itemId, null, offeringId, versionId, itemId);
  }

  private Long execution(Long orgId, String sourceType, Long sourceBusinessId, Long examId,
                         Long offeringId, Long versionId, Long itemId) {
    AssessmentExecution execution = new AssessmentExecution();
    execution.setOrganizationId(orgId);
    execution.setSourceType(sourceType);
    execution.setSourceBusinessId(sourceBusinessId);
    execution.setExamId(examId);
    execution.setCourseOfferingId(offeringId);
    execution.setCourseVersionId(versionId);
    execution.setCourseItemId(itemId);
    execution.setAssessmentKind(examId == null ? "QUIZ" : "FORMAL_EXAM");
    execution.setResourceType("PAPER_VERSION");
    execution.setResourceId(1L);
    execution.setResourceNameSnapshot("试卷");
    execution.setDurationSecondsSnapshot(60);
    execution.setTotalScoreSnapshot(new BigDecimal("100"));
    execution.setPassScoreSnapshot(new BigDecimal("60"));
    execution.setQuestionOrderModeSnapshot("IMPORT");
    execution.setMultiSelectScoringSnapshot("ALL_OR_NOTHING");
    execution.setPolicySnapshotJson("{}");
    execution.setExecutionStatus("READY");
    new AssessmentExecutionSnapshotCodec(new ObjectMapper()).encode(execution);
    assessmentExecutionMapper.insert(execution);
    return execution.getAssessmentExecutionId();
  }
}

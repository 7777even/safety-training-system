package com.sts.exam.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sts.admin.support.AbstractMysqlIntegrationTest;
import com.sts.exam.entity.*;
import com.sts.exam.mapper.*;
import com.sts.exam.support.ExamMapperItConfig;
import com.sts.org.mapper.OrganizationMapper;
import com.sts.paper.entity.Paper;
import com.sts.paper.entity.PaperVersion;
import com.sts.paper.mapper.PaperMapper;
import com.sts.paper.mapper.PaperVersionMapper;
import com.sts.question.mapper.QuestionBankMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest(classes = ExamMapperItConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import({ExamCandidateDeleteHelper.class, AssessmentExecutionDeleteHelper.class})
@Transactional
class DraftExamParticipantLifecycleIT extends AbstractMysqlIntegrationTest {

  @Autowired
  private ExamMapper examMapper;
  @Autowired
  private ExamPolicyMapper policyMapper;
  @Autowired
  private AssessmentExecutionMapper executionMapper;
  @Autowired
  private ExamCandidateMapper candidateMapper;
  @Autowired
  private ExamReviewerMapper reviewerMapper;
  @Autowired
  private ExamAttemptMapper attemptMapper;
  @Autowired
  private OrganizationMapper organizationMapper;
  @Autowired
  private ExamCandidateDeleteHelper candidateDeleteHelper;
  @Autowired
  private AssessmentExecutionDeleteHelper executionDeleteHelper;
  @Autowired
  private JdbcTemplate jdbcTemplate;
  @Autowired
  private PlatformTransactionManager transactionManager;

  @Test
  @DisplayName("草稿执行归属：发布刷新保留已配置人员，显式删除后唯一键可复用")
  void refreshPreservesParticipants_thenDeleteCleansOwnershipAndAllowsRecreate() {
    Exam exam = insertDraftExam("DRAFT-LIFECYCLE-1", 30L);
    insertPolicy(exam.getExamId());
    AssessmentExecutionService service = serviceWithPaperVersions();
    AssessmentExecution execution = service.ensureStandalone(exam);
    assertThat(service.ensureStandalone(exam).getAssessmentExecutionId())
      .isEqualTo(execution.getAssessmentExecutionId());
    ExamCandidate candidate = insertCandidate(execution.getAssessmentExecutionId(), 701L);
    insertReviewer(execution.getAssessmentExecutionId(), 801L);

    exam.setPaperVersionId(40L);
    exam.setDurationSeconds(1800);
    exam.setPassScore(new BigDecimal("55.00"));
    AssessmentExecution refreshed = service.refreshStandalone(exam);

    AssessmentExecution unchanged = service.requireStandaloneByExamId(exam.getExamId());
    assertThat(refreshed.getAssessmentExecutionId()).isEqualTo(execution.getAssessmentExecutionId());
    assertThat(unchanged.getResourceId()).isEqualTo(40L);
    assertThat(candidateMapper.selectById(candidate.getExamCandidateId()).getAssessmentExecutionId())
      .isEqualTo(execution.getAssessmentExecutionId());
    assertThat(reviewerMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ExamReviewer>()
                                            .eq(ExamReviewer::getAssessmentExecutionId,
                                                execution.getAssessmentExecutionId())))
      .isEqualTo(1L);

    service.deleteStandaloneIfUnused(exam.getExamId());

    assertThat(jdbcTemplate.queryForObject(
      "SELECT deleted FROM sts_exam_candidate WHERE exam_candidate_id = ?", Long.class,
      candidate.getExamCandidateId())).isEqualTo(candidate.getExamCandidateId());
    assertThat(jdbcTemplate.queryForObject(
      "SELECT deleted FROM sts_assessment_execution WHERE assessment_execution_id = ?", Long.class,
      execution.getAssessmentExecutionId())).isEqualTo(execution.getAssessmentExecutionId());
    assertThat(jdbcTemplate.queryForObject(
      "SELECT COUNT(*) FROM sts_exam_reviewer WHERE assessment_execution_id = ?", Long.class,
      execution.getAssessmentExecutionId())).isZero();

    AssessmentExecution recreated = service.ensureStandalone(exam);
    assertThat(recreated.getAssessmentExecutionId()).isNotEqualTo(execution.getAssessmentExecutionId());
  }

  @Test
  @DisplayName("草稿执行归属：异常存在作答时拒绝删除全部关联")
  void delete_rejectsExecutionWithAttempt() {
    Exam exam = insertDraftExam("DRAFT-LIFECYCLE-2", 30L);
    AssessmentExecutionService service = serviceWithPaperVersions();
    AssessmentExecution execution = service.ensureStandalone(exam);
    ExamCandidate candidate = insertCandidate(execution.getAssessmentExecutionId(), 702L);
    insertReviewer(execution.getAssessmentExecutionId(), 802L);
    insertAttempt(execution, candidate);

    assertThatThrownBy(() -> service.deleteStandaloneIfUnused(exam.getExamId()))
      .isInstanceOfSatisfying(com.sts.common.exception.BusinessException.class,
                              ex -> assertThat(ex.getCode()).isEqualTo(7003));

    assertThat(executionMapper.selectById(execution.getAssessmentExecutionId())).isNotNull();
    assertThat(candidateMapper.selectById(candidate.getExamCandidateId())).isNotNull();
    assertThat(reviewerMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ExamReviewer>()
                                            .eq(ExamReviewer::getAssessmentExecutionId,
                                                execution.getAssessmentExecutionId())))
      .isEqualTo(1L);
  }

  @Test
  @DisplayName("发布状态更新失败时执行快照刷新整体回滚")
  void publish_updateConflict_rollsBackSnapshotRefresh() {
    Exam exam = insertDraftOfflineExam("DRAFT-PUBLISH-ROLLBACK");
    AssessmentExecutionService executionService = offlineExecutionService();
    AssessmentExecution execution = executionService.ensureStandalone(exam);
    String originalHash = execution.getSnapshotHash();
    exam.setExamName("发布前最终名称");
    examMapper.updateById(exam);

    ExamMapper conflictingMapper = mock(ExamMapper.class,
                                        org.mockito.Mockito.withSettings().defaultAnswer(delegatesTo(examMapper)));
    doReturn(0).when(conflictingMapper).updateById(any(Exam.class));
    ExamService service = examService(conflictingMapper, reviewerMapper, executionService);

    assertThatThrownBy(() -> nestedTransaction().executeWithoutResult(status -> service.publish(exam.getExamId())))
      .isInstanceOfSatisfying(com.sts.common.exception.BusinessException.class,
                              ex -> assertThat(ex.getCode()).isEqualTo(7004));

    assertThat(jdbcTemplate.queryForObject(
      "SELECT snapshot_hash FROM sts_assessment_execution WHERE assessment_execution_id = ?",
      String.class, execution.getAssessmentExecutionId())).isEqualTo(originalHash);
    assertThat(jdbcTemplate.queryForObject(
      "SELECT publish_status FROM sts_exam_exam WHERE exam_id = ?",
      String.class, exam.getExamId())).isEqualTo("DRAFT");
  }

  @Test
  @DisplayName("批阅人整体替换失败时删除操作回滚")
  void replaceReviewers_insertFailure_rollsBackDelete() {
    Exam exam = insertDraftOfflineExam("DRAFT-REVIEWER-ROLLBACK");
    AssessmentExecutionService executionService = offlineExecutionService();
    AssessmentExecution execution = executionService.ensureStandalone(exam);
    insertReviewer(execution.getAssessmentExecutionId(), 901L);
    ExamReviewerMapper failingMapper = mock(ExamReviewerMapper.class,
                                            org.mockito.Mockito.withSettings().defaultAnswer(delegatesTo(reviewerMapper)));
    doThrow(new IllegalStateException("模拟批阅人写入失败")).when(failingMapper).insert(any(ExamReviewer.class));
    ExamService service = examService(examMapper, failingMapper, executionService);
    com.sts.exam.dto.ExamReviewersReplaceRequest request = new com.sts.exam.dto.ExamReviewersReplaceRequest();
    request.setReviewerUserIds(java.util.List.of(902L));
    request.setAssignmentSource("USER");

    assertThatThrownBy(() -> nestedTransaction()
      .executeWithoutResult(status -> service.replaceReviewers(exam.getExamId(), request)))
      .isInstanceOf(IllegalStateException.class);

    assertThat(jdbcTemplate.queryForObject(
      "SELECT COUNT(*) FROM sts_exam_reviewer WHERE assessment_execution_id = ? AND reviewer_user_id = ?",
      Long.class, execution.getAssessmentExecutionId(), 901L)).isEqualTo(1L);
    assertThat(jdbcTemplate.queryForObject(
      "SELECT COUNT(*) FROM sts_exam_reviewer WHERE assessment_execution_id = ? AND reviewer_user_id = ?",
      Long.class, execution.getAssessmentExecutionId(), 902L)).isZero();
  }

  private TransactionTemplate nestedTransaction() {
    TransactionTemplate nested = new TransactionTemplate(transactionManager);
    nested.setPropagationBehavior(TransactionDefinition.PROPAGATION_NESTED);
    return nested;
  }

  private AssessmentExecutionService serviceWithPaperVersions() {
    PaperVersionMapper versionMapper = mock(PaperVersionMapper.class);
    PaperMapper paperMapper = mock(PaperMapper.class);
    when(versionMapper.selectById(30L)).thenReturn(paperVersion(30L, 3L, "100.00", "60.00"));
    when(versionMapper.selectById(40L)).thenReturn(paperVersion(40L, 4L, "120.00", "72.00"));
    Paper first = new Paper();
    first.setPaperId(3L);
    first.setPaperName("预备试卷");
    Paper last = new Paper();
    last.setPaperId(4L);
    last.setPaperName("最终试卷");
    when(paperMapper.selectById(3L)).thenReturn(first);
    when(paperMapper.selectById(4L)).thenReturn(last);
    return new AssessmentExecutionServiceImpl(
      executionMapper, policyMapper, versionMapper, paperMapper, mock(QuestionBankMapper.class),
      new ObjectMapper(), new AssessmentExecutionSnapshotCodec(new ObjectMapper()),
      candidateMapper, reviewerMapper, attemptMapper,
      mock(AssessmentExecutionQuestionPoolMapper.class),
      mock(com.sts.course.mapper.CourseAssessmentResultMapper.class),
      candidateDeleteHelper, executionDeleteHelper, org.mockito.Mockito.mock(com.sts.exam.service.CourseKnowledgeSnapshotService.class));
  }

  private AssessmentExecutionService offlineExecutionService() {
    return new AssessmentExecutionServiceImpl(
      executionMapper, policyMapper, mock(PaperVersionMapper.class), mock(PaperMapper.class),
      mock(QuestionBankMapper.class), new ObjectMapper(), new AssessmentExecutionSnapshotCodec(new ObjectMapper()),
      candidateMapper, reviewerMapper, attemptMapper,
      mock(AssessmentExecutionQuestionPoolMapper.class),
      mock(com.sts.course.mapper.CourseAssessmentResultMapper.class),
      candidateDeleteHelper, executionDeleteHelper, org.mockito.Mockito.mock(com.sts.exam.service.CourseKnowledgeSnapshotService.class));
  }

  private ExamService examService(ExamMapper mapper, ExamReviewerMapper reviewers,
                                  AssessmentExecutionService executionService) {
    return new ExamServiceImpl(
      mapper, policyMapper, candidateMapper, reviewers, mock(PaperVersionMapper.class), mock(PaperMapper.class),
      mock(QuestionBankMapper.class),
      mock(com.sts.person.service.PersonSelectionService.class), mock(ExamPolicyDeleteHelper.class),
      mock(ExamCandidateProfileSnapshotAssembler.class), mock(com.sts.exam.mapper.ExamReviewerQueryMapper.class),
      mock(com.sts.exam.mapper.ExamCandidateQueryMapper.class), executionService, new ObjectMapper(), mock(ExamReviewerEligibility.class));
  }

  private PaperVersion paperVersion(long versionId, long paperId, String total, String pass) {
    PaperVersion version = new PaperVersion();
    version.setPaperVersionId(versionId);
    version.setPaperId(paperId);
    version.setPublishedStatus("PUBLISHED");
    version.setDurationSeconds(3600);
    version.setTotalScore(new BigDecimal(total));
    version.setPassScore(new BigDecimal(pass));
    version.setQuestionOrderMode("IMPORT");
    version.setMultiSelectScoring("ALL_OR_NOTHING");
    return version;
  }

  private Exam insertDraftExam(String code, long paperVersionId) {
    Exam exam = new Exam();
    exam.setOrganizationId(organizationMapper.selectByOrgCode("DEMO").getOrganizationId());
    exam.setExamCode(code);
    exam.setExamName(code + "-名称");
    exam.setExamType("ONLINE");
    exam.setPaperVersionId(paperVersionId);
    exam.setDurationSeconds(3600);
    exam.setPassRatio(new BigDecimal("0.60"));
    exam.setPassScore(new BigDecimal("60.00"));
    exam.setCredit(BigDecimal.ZERO);
    exam.setQuestionOrderMode("IMPORT");
    exam.setMultiSelectScoring("ALL_OR_NOTHING");
    exam.setIsNoticeEnabled(0);
    exam.setPublishStatus("DRAFT");
    exam.setStatus(1);
    examMapper.insert(exam);
    return exam;
  }

  private Exam insertDraftOfflineExam(String code) {
    Exam exam = insertDraftExam(code, 30L);
    exam.setExamType("OFFLINE");
    exam.setPaperVersionId(null);
    examMapper.updateById(exam);
    return exam;
  }

  private void insertPolicy(long examId) {
    ExamPolicy policy = new ExamPolicy();
    policy.setExamId(examId);
    policy.setIsWatermarkEnabled(1);
    policy.setIsPcAllowed(1);
    policy.setIsAppAllowed(1);
    policy.setIsH5Allowed(0);
    policy.setSwitchLimit(3);
    policy.setIsSplitScreenForceSubmit(1);
    policy.setIsCopyRefreshDevtoolDisabled(1);
    policy.setInactivitySeconds(300);
    policy.setInactivityCountdownSeconds(30);
    policy.setMinimumAnswerSeconds(60);
    policy.setIsRetakeEnabled(0);
    policy.setRetakeMaxCount(0);
    policy.setRetakeScoreMode("HIGHEST");
    policy.setIsRetakeRecordVisible(1);
    policy.setPassPoint(new BigDecimal("2.50"));
    policy.setFailPoint(BigDecimal.ZERO);
    policy.setAbsentPoint(BigDecimal.ZERO);
    policy.setLateForbidSeconds(0);
    policy.setAnswerDetailMode("SUBMITTED");
    policy.setIsAnalysisVisible(1);
    policy.setIsScoreVisible(1);
    policy.setIsRankingEnabled(0);
    policyMapper.insert(policy);
  }

  private ExamCandidate insertCandidate(long executionId, long userId) {
    ExamCandidate candidate = new ExamCandidate();
    candidate.setOrganizationId(demoTenantId());
    candidate.setAssessmentExecutionId(executionId);
    candidate.setUserId(userId);
    candidate.setCourseOfferingId(0L);
    candidate.setCourseItemId(0L);
    candidate.setCourseEnrollmentId(0L);
    candidate.setAssignmentSource("ASSIGN");
    candidate.setSourceBusinessId(0L);
    candidate.setCandidateProfileSnapshotJson("{\"userId\":" + userId + "}");
    candidate.setProfileSnapshotTime(LocalDateTime.now());
    candidate.setCandidateStatus("NOT_STARTED");
    candidate.setAttemptCount(0);
    candidateMapper.insert(candidate);
    return candidate;
  }

  private void insertReviewer(long executionId, long userId) {
    ExamReviewer reviewer = new ExamReviewer();
    reviewer.setAssessmentExecutionId(executionId);
    reviewer.setReviewerUserId(userId);
    reviewer.setAssignmentSource("USER");
    reviewerMapper.insert(reviewer);
  }

  private void insertAttempt(AssessmentExecution execution, ExamCandidate candidate) {
    ExamAttempt attempt = new ExamAttempt();
    attempt.setOrganizationId(demoTenantId());
    attempt.setAssessmentExecutionId(execution.getAssessmentExecutionId());
    attempt.setSourceType(execution.getSourceType());
    attempt.setAssessmentKind(execution.getAssessmentKind());
    attempt.setExamCandidateId(candidate.getExamCandidateId());
    attempt.setUserId(candidate.getUserId());
    attempt.setPaperVersionId(execution.getResourceId());
    attempt.setPaperSourceType(execution.getResourceType());
    attempt.setPaperSourceId(execution.getResourceId());
    attempt.setTotalScoreSnapshot(execution.getTotalScoreSnapshot());
    attempt.setPassScoreSnapshot(execution.getPassScoreSnapshot());
    attempt.setDurationSecondsSnapshot(execution.getDurationSecondsSnapshot());
    attempt.setQuestionOrderModeSnapshot(execution.getQuestionOrderModeSnapshot());
    attempt.setMultiSelectScoringSnapshot(execution.getMultiSelectScoringSnapshot());
    attempt.setAttemptNo(1);
    attempt.setAttemptType("FIRST");
    attempt.setAttemptStatus("ANSWERING");
    attempt.setUsedSeconds(0);
    attempt.setObjectiveScore(BigDecimal.ZERO);
    attempt.setSubjectiveScore(BigDecimal.ZERO);
    attempt.setFinalScore(BigDecimal.ZERO);
    attempt.setCorrectCount(0);
    attempt.setWrongCount(0);
    attempt.setClientAttemptToken("draft-attempt-" + candidate.getUserId());
    attemptMapper.insert(attempt);
  }
}

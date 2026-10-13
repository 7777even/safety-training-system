package com.sts.exam.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sts.admin.support.AbstractMysqlIntegrationTest;
import com.sts.admin.tenant.TenantContext;
import com.sts.exam.dto.ExamAttemptRecordQuery;
import com.sts.exam.entity.AssessmentExecution;
import com.sts.exam.entity.Exam;
import com.sts.exam.entity.ExamAttempt;
import com.sts.exam.entity.ExamCandidate;
import com.sts.exam.service.AssessmentExecutionSnapshotCodec;
import com.sts.exam.support.ExamMapperItConfig;
import com.sts.exam.vo.ExamAttemptRecordVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 考试与作答记录的租户边界（Testcontainers 真 MySQL）。
 * <p>
 * 作答记录是判分、批阅、成绩发布的共同入口，一旦按 ID 能跨租户读到就等于成绩泄露。
 * 这里同时验证按 ID 直查被拒、更新影响 0 行，以及手写分页 SQL 也带上了租户条件。
 */
@SpringBootTest(classes = ExamMapperItConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class ExamTenantBoundaryIT extends AbstractMysqlIntegrationTest {

  @Autowired
  private ExamMapper examMapper;
  @Autowired
  private ExamAttemptMapper attemptMapper;
  @Autowired
  private ExamCandidateMapper candidateMapper;
  @Autowired
  private ExamAttemptQueryMapper attemptQueryMapper;
  @Autowired
  private AssessmentExecutionMapper assessmentExecutionMapper;
  @Autowired
  private JdbcTemplate jdbcTemplate;

  private Long otherTenantId() {
    jdbcTemplate.update(
      "INSERT INTO sts_org_organization (org_code, org_name, org_type, status, description)"
        + " VALUES ('EXAM-OTHER', '另一家企业', 'ENTERPRISE', 1, '')");
    return jdbcTemplate.queryForObject(
      "SELECT organization_id FROM sts_org_organization WHERE org_code = 'EXAM-OTHER'", Long.class);
  }

  private Long insertExam(String code) {
    Exam exam = new Exam();
    exam.setExamCode(code);
    exam.setExamName("考试-" + code);
    exam.setExamType("ONLINE");
    exam.setDurationSeconds(3600);
    exam.setPassRatio(new BigDecimal("0.6000"));
    exam.setPassScore(new BigDecimal("60.00"));
    exam.setCredit(BigDecimal.ONE);
    exam.setQuestionOrderMode("TYPE");
    exam.setMultiSelectScoring("ALL_OR_NOTHING");
    exam.setIsNoticeEnabled(0);
    exam.setPublishStatus("PUBLISHED");
    exam.setStatus(1);
    examMapper.insert(exam);
    return exam.getExamId();
  }

  private Long insertCandidate(Long assessmentExecutionId, long userId) {
    ExamCandidate candidate = new ExamCandidate();
    candidate.setAssessmentExecutionId(assessmentExecutionId);
    candidate.setUserId(userId);
    candidate.setCourseOfferingId(0L);
    candidate.setCourseItemId(0L);
    candidate.setCourseEnrollmentId(0L);
    candidate.setAssignmentSource("IMPORT");
    candidate.setSourceBusinessId(0L);
    candidate.setCandidateProfileSnapshotJson("{\"userName\":\"考生\",\"userCode\":\"C-" + userId + "\"}");
    candidate.setProfileSnapshotTime(LocalDateTime.now());
    candidate.setCandidateStatus("SUBMITTED");
    candidate.setAttemptCount(1);
    candidateMapper.insert(candidate);
    return candidate.getExamCandidateId();
  }

  // 作答记录必须挂在真实考试人员上：分页 SQL 内连 sts_exam_candidate，缺行会假绿
  private Long insertAttempt(Long examId, long userId, String token) {
    Long assessmentExecutionId = insertExecution(examId);
    ExamAttempt attempt = new ExamAttempt();
    attempt.setAssessmentExecutionId(assessmentExecutionId);
    attempt.setSourceType("STANDALONE_EXAM");
    attempt.setAssessmentKind("FORMAL_EXAM");
    attempt.setExamCandidateId(insertCandidate(assessmentExecutionId, userId));
    attempt.setUserId(userId);
    attempt.setPaperSourceType("QUESTION_BANK");
    attempt.setTotalScoreSnapshot(new BigDecimal("100.00"));
    attempt.setPassScoreSnapshot(new BigDecimal("60.00"));
    attempt.setDurationSecondsSnapshot(3600);
    attempt.setQuestionOrderModeSnapshot("TYPE");
    attempt.setMultiSelectScoringSnapshot("ALL_OR_NOTHING");
    attempt.setAttemptNo(1);
    attempt.setAttemptType("FIRST");
    attempt.setAttemptStatus("SUBMITTED");
    attempt.setUsedSeconds(600);
    attempt.setObjectiveScore(new BigDecimal("80.00"));
    attempt.setSubjectiveScore(BigDecimal.ZERO);
    attempt.setFinalScore(new BigDecimal("80.00"));
    attempt.setCorrectCount(8);
    attempt.setWrongCount(2);
    attempt.setClientAttemptToken(token);
    attemptMapper.insert(attempt);
    return attempt.getExamAttemptId();
  }

  @Test
  @DisplayName("跨租户按 ID 直查考试与作答记录一律返回空")
  void selectById_isRejectedAcrossTenants() {
    Long other = otherTenantId();
    Long examId = TenantContext.callWithTenant(other, () -> insertExam("EXAM-CROSS"));
    Long attemptId = TenantContext.callWithTenant(other, () -> insertAttempt(examId, 9001L, "TOKEN-CROSS"));

    assertThat(examMapper.selectById(examId)).isNull();
    assertThat(attemptMapper.selectById(attemptId)).isNull();
  }

  @Test
  @DisplayName("跨租户改判分数影响 0 行：判分与成绩发布写不到别家作答记录")
  void updateById_cannotTouchOtherTenantAttempt() {
    Long other = otherTenantId();
    Long examId = TenantContext.callWithTenant(other, () -> insertExam("EXAM-UPD"));
    Long attemptId = TenantContext.callWithTenant(other, () -> insertAttempt(examId, 9002L, "TOKEN-UPD"));

    ExamAttempt tampered = new ExamAttempt();
    tampered.setExamAttemptId(attemptId);
    tampered.setFinalScore(new BigDecimal("100.00"));
    assertThat(attemptMapper.updateById(tampered)).isZero();

    BigDecimal stored = jdbcTemplate.queryForObject(
      "SELECT final_score FROM sts_exam_attempt WHERE exam_attempt_id = ?", BigDecimal.class, attemptId);
    assertThat(stored).isEqualByComparingTo("80.00");
  }

  @Test
  @DisplayName("手写作答分页 SQL 只返回本租户记录，且 total 同步收敛")
  void attemptPage_countsOnlyOwnTenant() {
    Long other = otherTenantId();
    Long ownExamId = insertExam("EXAM-OWN");
    insertAttempt(ownExamId, 9003L, "TOKEN-OWN");
    Long otherExamId = TenantContext.callWithTenant(other, () -> insertExam("EXAM-HIDDEN"));
    TenantContext.runWithTenant(other, () -> insertAttempt(otherExamId, 9004L, "TOKEN-HIDDEN"));

    Page<ExamAttemptRecordVO> page = attemptQueryMapper.selectAttemptPage(
      new Page<>(1, 50), new ExamAttemptRecordQuery());

    assertThat(page.getRecords()).hasSize(1);
    assertThat(page.getTotal())
      .describedAs("分页 count 必须与列表一致，否则租户插件排在分页插件之后").isEqualTo(1);
  }

  @Test
  @DisplayName("按考试 ID 列作答记录时，别家同名考试的记录不会混入")
  void listByExamId_isTenantScoped() {
    Long other = otherTenantId();
    Long otherExamId = TenantContext.callWithTenant(other, () -> insertExam("EXAM-LIST"));
    TenantContext.runWithTenant(other, () -> insertAttempt(otherExamId, 9005L, "TOKEN-LIST"));

    Long otherExecutionId = jdbcTemplate.queryForObject(
      "SELECT assessment_execution_id FROM sts_assessment_execution WHERE exam_id = ?", Long.class, otherExamId);
    assertThat(attemptMapper.selectList(new LambdaQueryWrapper<ExamAttempt>()
                                          .eq(ExamAttempt::getAssessmentExecutionId, otherExecutionId))).isEmpty();
  }

  private Long insertExecution(Long examId) {
    AssessmentExecution execution = new AssessmentExecution();
    execution.setOrganizationId(TenantContext.requireTenantId());
    execution.setSourceType("STANDALONE_EXAM");
    execution.setSourceBusinessId(examId);
    execution.setExamId(examId);
    execution.setAssessmentKind("FORMAL_EXAM");
    execution.setResourceType("PAPER_VERSION");
    execution.setResourceId(1L);
    execution.setResourceNameSnapshot("试卷");
    execution.setDurationSecondsSnapshot(3600);
    execution.setTotalScoreSnapshot(new BigDecimal("100"));
    execution.setPassScoreSnapshot(new BigDecimal("60"));
    execution.setQuestionOrderModeSnapshot("TYPE");
    execution.setMultiSelectScoringSnapshot("ALL_OR_NOTHING");
    execution.setPolicySnapshotJson("{}");
    execution.setExecutionStatus("READY");
    new AssessmentExecutionSnapshotCodec(new ObjectMapper()).encode(execution);
    assessmentExecutionMapper.insert(execution);
    return execution.getAssessmentExecutionId();
  }
}

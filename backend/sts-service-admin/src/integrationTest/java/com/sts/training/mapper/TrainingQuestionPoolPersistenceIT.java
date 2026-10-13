package com.sts.training.mapper;

import com.sts.admin.support.AbstractMysqlIntegrationTest;
import com.sts.exam.entity.AssessmentExecutionQuestionPool;
import com.sts.exam.entity.ExamAnswer;
import com.sts.exam.entity.ExamAttempt;
import com.sts.exam.entity.ExamAttemptQuestion;
import com.sts.exam.mapper.AssessmentExecutionQuestionPoolMapper;
import com.sts.exam.mapper.ExamAnswerMapper;
import com.sts.exam.mapper.ExamAttemptMapper;
import com.sts.exam.mapper.ExamAttemptQuestionMapper;
import com.sts.org.mapper.OrganizationMapper;
import com.sts.training.entity.TrainingParticipantContentResult;
import com.sts.training.entity.TrainingPlanQuestionPool;
import com.sts.training.support.TrainingMixedPlanItConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = TrainingMixedPlanItConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class TrainingQuestionPoolPersistenceIT extends AbstractMysqlIntegrationTest {
  @Autowired
  private TrainingPlanQuestionPoolMapper planPoolMapper;
  @Autowired
  private AssessmentExecutionQuestionPoolMapper executionPoolMapper;
  @Autowired
  private OrganizationMapper organizationMapper;
  @Autowired
  private ExamAttemptMapper attemptMapper;
  @Autowired
  private ExamAttemptQuestionMapper attemptQuestionMapper;
  @Autowired
  private ExamAnswerMapper answerMapper;
  @Autowired
  private TrainingParticipantContentResultMapper contentResultMapper;

  @Test
  @DisplayName("两级冻结题池保存精确版本并以执行边界禁止重复题目")
  void persistsExactVersionsAndUniqueBoundaries() {
    Long organizationId = organizationMapper.selectByOrgCode(DEMO_ORG_CODE).getOrganizationId();
    TrainingPlanQuestionPool planRow = planRow(organizationId);
    assertThat(planPoolMapper.insert(planRow)).isEqualTo(1);
    assertThat(planPoolMapper.selectById(planRow.getPlanQuestionPoolId()).getQuestionVersionId()).isEqualTo(101L);
    assertThatThrownBy(() -> planPoolMapper.insert(planRow(organizationId))).isInstanceOf(DuplicateKeyException.class);

    AssessmentExecutionQuestionPool executionRow = executionRow(organizationId);
    assertThat(executionPoolMapper.insert(executionRow)).isEqualTo(1);
    AssessmentExecutionQuestionPool loaded = executionPoolMapper.selectById(executionRow.getExecutionQuestionPoolId());
    assertThat(loaded.getQuestionVersionId()).isEqualTo(101L);
    assertThat(loaded.getEvaluationMode()).isEqualTo("RECORD_ONLY");
    assertThatThrownBy(() -> executionPoolMapper.insert(executionRow(organizationId))).isInstanceOf(
      DuplicateKeyException.class);
  }

  @Test
  @DisplayName("不评分练习通过 Mapper 持久化空分数、抽题依据和判定汇总")
  void persistsUnscoredPracticeFacts() {
    Long organizationId = organizationMapper.selectByOrgCode(DEMO_ORG_CODE).getOrganizationId();
    ExamAttempt attempt = new ExamAttempt();
    attempt.setOrganizationId(organizationId);
    attempt.setAssessmentExecutionId(910001L);
    attempt.setSourceType("TRAINING_PRACTICE");
    attempt.setAssessmentKind("BANK_PRACTICE");
    attempt.setExamCandidateId(910002L);
    attempt.setUserId(910003L);
    attempt.setPaperSourceType("QUESTION_BANK");
    attempt.setPaperSourceId(910004L);
    attempt.setDurationSecondsSnapshot(0);
    attempt.setQuestionOrderModeSnapshot("RANDOM");
    attempt.setMultiSelectScoringSnapshot("ALL_OR_NOTHING");
    attempt.setAttemptNo(1);
    attempt.setAttemptType("PRACTICE");
    attempt.setAttemptStatus("RESULT_EFFECTIVE");
    attempt.setUsedSeconds(30);
    attempt.setCorrectCount(1);
    attempt.setWrongCount(0);
    attempt.setEvaluatedCount(1);
    attempt.setUnassessedCount(1);
    attempt.setAccuracyRate(new java.math.BigDecimal("100.00"));
    attempt.setClientAttemptToken("unscored-it-token");
    attempt.setDrawSeed(20260911L);
    attemptMapper.insert(attempt);

    ExamAttemptQuestion question = new ExamAttemptQuestion();
    question.setOrganizationId(organizationId);
    question.setExamAttemptId(attempt.getExamAttemptId());
    question.setQuestionId(920001L);
    question.setQuestionVersionId(920002L);
    question.setQuestionType("ESSAY");
    question.setStemSnapshot("简答题");
    question.setGradingMode("MANUAL");
    question.setSortOrder(1);
    question.setSourceBankId(910004L);
    question.setDrawRuleIndex(2);
    question.setEvaluationMode("RECORD_ONLY");
    attemptQuestionMapper.insert(question);

    ExamAnswer answer = new ExamAnswer();
    answer.setOrganizationId(organizationId);
    answer.setExamAttemptId(attempt.getExamAttemptId());
    answer.setAttemptQuestionId(question.getAttemptQuestionId());
    answer.setAnswerText("学习记录");
    answer.setIsAnswered(1);
    answer.setGradingStatus("NOT_EVALUATED");
    answer.setEvaluationResult("NOT_EVALUATED");
    answerMapper.insert(answer);

    ExamAttempt loadedAttempt = attemptMapper.selectById(attempt.getExamAttemptId());
    assertThat(loadedAttempt.getFinalScore()).isNull();
    assertThat(loadedAttempt.getAccuracyRate()).isEqualByComparingTo("100.00");
    assertThat(loadedAttempt.getDrawSeed()).isEqualTo(20260911L);
    assertThat(attemptQuestionMapper.selectById(question.getAttemptQuestionId()).getScore()).isNull();
    ExamAnswer loadedAnswer = answerMapper.selectById(answer.getExamAnswerId());
    assertThat(loadedAnswer.getFinalScore()).isNull();
    assertThat(loadedAnswer.getIsCorrect()).isNull();
    assertThat(loadedAnswer.getEvaluationResult()).isEqualTo("NOT_EVALUATED");

    TrainingParticipantContentResult projection = new TrainingParticipantContentResult();
    projection.setOrganizationId(organizationId);
    projection.setTrainingPlanId(930001L);
    projection.setTrainingParticipantId(930002L);
    projection.setPlanContentId(930003L);
    projection.setContentType("QUESTION_BANK");
    projection.setFinalAttemptId(attempt.getExamAttemptId());
    projection.setEvaluatedCount(1);
    projection.setCorrectCount(1);
    projection.setWrongCount(0);
    projection.setUnassessedCount(1);
    projection.setAccuracyRate(new java.math.BigDecimal("100.00"));
    projection.setResultStatus("COMPLETED");
    projection.setEffectiveDurationSeconds(0);
    projection.setResultRevision(1);
    contentResultMapper.insert(projection);
    TrainingParticipantContentResult loadedProjection =
      contentResultMapper.selectById(projection.getTrainingParticipantContentResultId());
    assertThat(loadedProjection.getUnassessedCount()).isEqualTo(1);
    assertThat(loadedProjection.getAccuracyRate()).isEqualByComparingTo("100.00");
  }

  private TrainingPlanQuestionPool planRow(Long organizationId) {
    TrainingPlanQuestionPool row = new TrainingPlanQuestionPool();
    row.setOrganizationId(organizationId);
    row.setTrainingPlanId(800001L);
    row.setPlanContentId(800002L);
    row.setQuestionBankId(800003L);
    row.setQuestionId(800004L);
    row.setQuestionVersionId(101L);
    row.setQuestionType("ESSAY");
    row.setDifficulty("NORMAL");
    row.setKnowledgePointIdsJson("[8]");
    row.setEvaluationMode("RECORD_ONLY");
    row.setCandidateOrder(1);
    row.setPoolHash("a".repeat(64));
    return row;
  }

  private AssessmentExecutionQuestionPool executionRow(Long organizationId) {
    AssessmentExecutionQuestionPool row = new AssessmentExecutionQuestionPool();
    row.setOrganizationId(organizationId);
    row.setAssessmentExecutionId(900001L);
    row.setSourcePlanContentId(800002L);
    row.setQuestionBankId(800003L);
    row.setQuestionId(800004L);
    row.setQuestionVersionId(101L);
    row.setQuestionType("ESSAY");
    row.setDifficulty("NORMAL");
    row.setKnowledgePointIdsJson("[8]");
    row.setEvaluationMode("RECORD_ONLY");
    row.setCandidateOrder(1);
    row.setPoolHash("a".repeat(64));
    return row;
  }
}

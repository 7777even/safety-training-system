package com.sts.exam.service;

import com.sts.common.exception.BusinessException;
import com.sts.exam.entity.AssessmentExecutionQuestionPool;
import com.sts.exam.mapper.AssessmentExecutionQuestionPoolMapper;
import com.sts.question.draw.QuestionDrawEngine;
import com.sts.training.port.TrainingQuestionPoolItem;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AssessmentExecutionQuestionPoolServiceTest {

  @Test
  void copiesFrozenPlanPoolAndLoadsSameVersions() {
    AssessmentExecutionQuestionPoolMapper mapper = mock(AssessmentExecutionQuestionPoolMapper.class);
    AssessmentExecutionQuestionPoolService service = new AssessmentExecutionQuestionPoolService(
      mapper, new com.fasterxml.jackson.databind.ObjectMapper());
    TrainingQuestionPoolItem item = new TrainingQuestionPoolItem();
    item.setQuestionId(11L);
    item.setQuestionVersionId(101L);
    item.setQuestionType("ESSAY");
    item.setDifficulty("NORMAL");
    item.setKnowledgePointIds(List.of(8L));
    item.setEvaluationMode("RECORD_ONLY");
    item.setCandidateOrder(1);
    String hash = com.sts.question.draw.QuestionPoolFingerprint.of(List.of(
      new com.sts.question.draw.QuestionPoolFingerprint.Item(11L, 101L, "ESSAY", "NORMAL",
                                                             List.of(8L), "RECORD_ONLY")));

    service.ensure(9L, 3L, 4L, hash, List.of(item));

    ArgumentCaptor<AssessmentExecutionQuestionPool> captor =
      ArgumentCaptor.forClass(AssessmentExecutionQuestionPool.class);
    verify(mapper).insert(captor.capture());
    when(mapper.selectList(any())).thenReturn(List.of(captor.getValue()));
    List<QuestionDrawEngine.Candidate> loaded = service.loadCandidates(9L, hash);
    assertThat(loaded).singleElement().satisfies(candidate -> {
      assertThat(candidate.questionVersionId()).isEqualTo(101L);
      assertThat(candidate.evaluationMode()).isEqualTo(QuestionDrawEngine.EvaluationMode.RECORD_ONLY);
    });
  }

  @Test
  void rejectsInvalidFrozenEvaluationModeWithStableBusinessError() {
    AssessmentExecutionQuestionPoolMapper mapper = mock(AssessmentExecutionQuestionPoolMapper.class);
    AssessmentExecutionQuestionPoolService service = new AssessmentExecutionQuestionPoolService(
      mapper, new com.fasterxml.jackson.databind.ObjectMapper());
    AssessmentExecutionQuestionPool row = new AssessmentExecutionQuestionPool();
    row.setAssessmentExecutionId(9L);
    row.setSourcePlanContentId(3L);
    row.setQuestionBankId(4L);
    row.setQuestionId(11L);
    row.setQuestionVersionId(101L);
    row.setQuestionType("ESSAY");
    row.setDifficulty("NORMAL");
    row.setKnowledgePointIdsJson("[8]");
    row.setEvaluationMode("BROKEN");
    row.setCandidateOrder(1);
    row.setPoolHash(com.sts.question.draw.QuestionPoolFingerprint.of(List.of(
      new com.sts.question.draw.QuestionPoolFingerprint.Item(11L, 101L, "ESSAY", "NORMAL",
                                                             List.of(8L), "BROKEN"))));
    when(mapper.selectList(any())).thenReturn(List.of(row));

    assertThatThrownBy(() -> service.loadCandidates(9L, row.getPoolHash()))
      .isInstanceOfSatisfying(BusinessException.class, ex -> assertThat(ex.getCode()).isEqualTo(8167));
  }
}

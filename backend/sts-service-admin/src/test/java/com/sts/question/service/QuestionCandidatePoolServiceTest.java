package com.sts.question.service;

import com.sts.question.draw.QuestionDrawEngine;
import com.sts.question.mapper.QuestionMapper;
import com.sts.question.vo.QuestionDrawCandidateRow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QuestionCandidatePoolServiceTest {

  @Test
  @DisplayName("候选池按题目聚合知识点并派生自动判定和只记录模式")
  void groupsKnowledgePointsAndResolvesEvaluationMode() {
    QuestionMapper mapper = mock(QuestionMapper.class);
    when(mapper.selectDrawCandidateRows(9L)).thenReturn(List.of(
      row(1L, 11L, "SINGLE", "AUTO", 1L, 1, 0),
      row(1L, 11L, "SINGLE", "AUTO", 2L, 1, 0),
      row(2L, 12L, "ESSAY", "MANUAL", null, 0, 0),
      row(3L, 13L, "SINGLE", "MANUAL", null, 1, 0)));

    List<QuestionDrawEngine.Candidate> candidates = new QuestionCandidatePoolService(
      mapper, new QuestionEvaluationModeResolver())
      .loadCandidates(9L);

    assertThat(candidates).hasSize(3);
    assertThat(candidates.get(0).knowledgePointIds()).containsExactlyInAnyOrder(1L, 2L);
    assertThat(candidates.get(0).evaluationMode()).isEqualTo(QuestionDrawEngine.EvaluationMode.AUTO);
    assertThat(candidates.get(1).evaluationMode()).isEqualTo(QuestionDrawEngine.EvaluationMode.RECORD_ONLY);
    assertThat(candidates.get(2).evaluationMode()).isEqualTo(QuestionDrawEngine.EvaluationMode.RECORD_ONLY);
  }

  @Test
  @DisplayName("已发布但答案资产异常的自动题标记为不支持，不能伪装为可判定")
  void marksIncompleteAutoQuestionUnsupported() {
    QuestionMapper mapper = mock(QuestionMapper.class);
    when(mapper.selectDrawCandidateRows(9L)).thenReturn(List.of(
      row(1L, 11L, "FILL", "AUTO", null, 0, 0)));

    List<QuestionDrawEngine.Candidate> candidates = new QuestionCandidatePoolService(
      mapper, new QuestionEvaluationModeResolver())
      .loadCandidates(9L);

    assertThat(candidates).singleElement().satisfies(candidate ->
                                                       assertThat(candidate.evaluationMode()).isEqualTo(
                                                         QuestionDrawEngine.EvaluationMode.UNSUPPORTED));
  }

  private QuestionDrawCandidateRow row(long questionId, long versionId, String type, String gradingMode,
                                       Long pointId, int correctCount, int blankCount) {
    QuestionDrawCandidateRow row = new QuestionDrawCandidateRow();
    row.setQuestionId(questionId);
    row.setQuestionVersionId(versionId);
    row.setQuestionType(type);
    row.setDifficulty("NORMAL");
    row.setGradingMode(gradingMode);
    row.setKnowledgePointId(pointId);
    row.setCorrectAnswerCount(correctCount);
    row.setBlankAnswerCount(blankCount);
    return row;
  }
}

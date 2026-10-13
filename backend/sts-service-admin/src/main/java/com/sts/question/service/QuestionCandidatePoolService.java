package com.sts.question.service;

import com.sts.question.draw.QuestionDrawEngine;
import com.sts.question.mapper.QuestionMapper;
import com.sts.question.vo.QuestionDrawCandidateRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 按真实开考口径装配题库候选：启用、未归档、最新已发布版本，并派生题目判定能力。
 */
@Service
@RequiredArgsConstructor
public class QuestionCandidatePoolService {

  private final QuestionMapper questionMapper;
  private final QuestionEvaluationModeResolver evaluationModeResolver;

  public List<QuestionDrawEngine.Candidate> loadCandidates(Long questionBankId) {
    List<QuestionDrawCandidateRow> rows = questionMapper.selectDrawCandidateRows(questionBankId);
    if (rows == null || rows.isEmpty()) {
      return List.of();
    }
    Map<Long, CandidateAccumulator> grouped = new LinkedHashMap<>();
    for (QuestionDrawCandidateRow row : rows) {
      if (row == null || row.getQuestionId() == null || row.getQuestionVersionId() == null) {
        continue;
      }
      CandidateAccumulator item = grouped.computeIfAbsent(row.getQuestionId(), ignored ->
        new CandidateAccumulator(row, new LinkedHashSet<>()));
      if (row.getKnowledgePointId() != null) {
        item.knowledgePointIds().add(row.getKnowledgePointId());
      }
    }
    return grouped.values().stream().map(item -> {
      QuestionDrawCandidateRow row = item.row();
      return new QuestionDrawEngine.Candidate(
        row.getQuestionId(), row.getQuestionVersionId(), row.getQuestionType(), row.getDifficulty(),
        item.knowledgePointIds(), evaluationModeResolver.resolve(row));
    }).toList();
  }

  private record CandidateAccumulator(QuestionDrawCandidateRow row, Set<Long> knowledgePointIds) {
  }
}

package com.sts.question.service;

import com.sts.question.draw.QuestionDrawEngine;
import com.sts.question.vo.QuestionDrawCandidateRow;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 集中派生题目版本的判定能力，避免培训、考试或页面分别猜测题型语义。
 */
@Component
public class QuestionEvaluationModeResolver {

  private static final Set<String> OPTION_TYPES = Set.of("SINGLE", "MULTIPLE", "JUDGE");
  private static final Set<String> KNOWN_TYPES = Set.of("SINGLE", "MULTIPLE", "JUDGE", "FILL", "ESSAY");

  public QuestionDrawEngine.EvaluationMode resolve(QuestionDrawCandidateRow row) {
    if (row == null || !KNOWN_TYPES.contains(row.getQuestionType())) {
      return QuestionDrawEngine.EvaluationMode.UNSUPPORTED;
    }
    if ("ESSAY".equals(row.getQuestionType()) || "MANUAL".equals(row.getGradingMode())) {
      return QuestionDrawEngine.EvaluationMode.RECORD_ONLY;
    }
    if (!"AUTO".equals(row.getGradingMode())) {
      return QuestionDrawEngine.EvaluationMode.UNSUPPORTED;
    }
    if (OPTION_TYPES.contains(row.getQuestionType())) {
      return positive(row.getCorrectAnswerCount())
        ? QuestionDrawEngine.EvaluationMode.AUTO : QuestionDrawEngine.EvaluationMode.UNSUPPORTED;
    }
    if ("FILL".equals(row.getQuestionType())) {
      return positive(row.getBlankAnswerCount())
        ? QuestionDrawEngine.EvaluationMode.AUTO : QuestionDrawEngine.EvaluationMode.UNSUPPORTED;
    }
    return QuestionDrawEngine.EvaluationMode.UNSUPPORTED;
  }

  private boolean positive(Integer count) {
    return count != null && count > 0;
  }
}

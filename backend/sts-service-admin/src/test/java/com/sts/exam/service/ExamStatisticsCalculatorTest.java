package com.sts.exam.service;

import com.sts.exam.entity.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

/** 考情人数、最终成绩与历史空值的口径回归。 */
class ExamStatisticsCalculatorTest {
  @Test void emptyRosterHasZeroCompletionAndNoFakeScores() {
    var result = ExamStatisticsCalculator.calculate(List.of(), List.of(), List.of(), List.of(), false);
    assertThat(result.expectedCount()).isZero();
    assertThat(result.completionRate()).isEqualByComparingTo("0");
    assertThat(result.averageScore()).isNull();
  }

  @Test void retakeDoesNotDuplicatePeopleAndOnlyFinalAnswerCounts() {
    var c = candidate(1L, "80", 12L);
    var old = attempt(11L, 1L, "60");
    var selected = attempt(12L, 1L, "80");
    var question = new ExamAttemptQuestion();
    question.setAttemptQuestionId(22L); question.setExamAttemptId(12L);
    question.setQuestionVersionId(7L); question.setQuestionType("SINGLE"); question.setStemSnapshot("题干");
    var answer = new ExamAnswer();
    answer.setAttemptQuestionId(22L); answer.setExamAttemptId(12L); answer.setIsCorrect(0);
    var result = ExamStatisticsCalculator.calculate(List.of(c), List.of(old, selected), List.of(question), List.of(answer), true);
    assertThat(result.attendedCount()).isEqualTo(1);
    assertThat(result.completedCount()).isEqualTo(1);
    assertThat(result.averageScore()).isEqualByComparingTo("80");
    assertThat(result.scoreBands().get(3).count()).isEqualTo(1);
    assertThat(result.wrongTop5()).hasSize(1);
    assertThat(result.platforms().get(0).label()).isEqualTo("未知");
  }

  @Test void unpublishedScoresAreExcludedAndAbsenceRequiresClosedWindow() {
    var c = candidate(1L, "0", 11L); c.setResultPublishedTime(null);
    var absent = candidate(2L, null, null);
    var result = ExamStatisticsCalculator.calculate(List.of(c, absent), List.of(attempt(11L, 1L, "0")), List.of(), List.of(), false);
    assertThat(result.averageScore()).isNull();
    assertThat(result.unpublishedCount()).isEqualTo(1);
    assertThat(result.absentCount()).isZero();
    assertThat(ExamStatisticsCalculator.calculate(List.of(c, absent), List.of(attempt(11L, 1L, "0")), List.of(), List.of(), true).absentCount()).isEqualTo(1);
  }

  @Test void rankingUsesCompetitionRankAndPreservesZeroScore() {
    var ranks = ExamStatisticsCalculator.ranks(List.of(candidate(1L,"90",11L),candidate(2L,"90",12L),candidate(3L,"0",13L)));
    assertThat(ranks).containsEntry(1L,1).containsEntry(2L,1).containsEntry(3L,3);
  }

  static ExamCandidate candidate(long id, String score, Long attemptId) {
    var c = new ExamCandidate(); c.setExamCandidateId(id); c.setUserId(id);
    c.setFinalScore(score == null ? null : new BigDecimal(score)); c.setFinalAttemptId(attemptId);
    if (score != null) c.setResultPublishedTime(LocalDateTime.now());
    return c;
  }
  static ExamAttempt attempt(long id, long candidate, String score) {
    var a = new ExamAttempt(); a.setExamAttemptId(id); a.setExamCandidateId(candidate);
    a.setAssessmentKind("FORMAL_EXAM"); a.setAttemptType("NORMAL");
    a.setStartedTime(LocalDateTime.now()); a.setSubmittedTime(LocalDateTime.now());
    a.setFinalScore(new BigDecimal(score)); a.setTotalScoreSnapshot(new BigDecimal("100")); a.setUsedSeconds(60);
    return a;
  }
}

package com.sts.question.draw;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class QuestionDrawEngineTest {

  @Test
  @DisplayName("宽规则与窄规则重叠时使用全局匹配，不因规则顺序误报候选不足")
  void overlappingRulesUseGlobalMatching() {
    List<QuestionDrawEngine.Candidate> pool = List.of(
      candidate(1L, "SINGLE", "EASY", 10L),
      candidate(2L, "SINGLE", "HARD", 10L));
    List<QuestionDrawEngine.Rule> rules = List.of(
      new QuestionDrawEngine.Rule(1, "SINGLE", "ALL", null, 1),
      new QuestionDrawEngine.Rule(2, "SINGLE", "EASY", 10L, 1));

    for (long seed = 1; seed <= 20; seed++) {
      QuestionDrawEngine.DrawResult result = QuestionDrawEngine.draw(pool, rules, seed);
      assertThat(result.assignments()).hasSize(2);
      assertThat(result.assignments()).extracting(QuestionDrawEngine.Assignment::questionId)
        .doesNotHaveDuplicates();
      assertThat(result.assignments()).filteredOn(item -> item.ruleIndex() == 2)
        .extracting(QuestionDrawEngine.Assignment::questionId).containsExactly(1L);
    }
  }

  @Test
  @DisplayName("同题型多规则按全局不重复口径稳定返回规则级可用数量")
  void validationReportsGlobalDeduplicatedShortage() {
    List<QuestionDrawEngine.Candidate> pool = List.of(
      candidate(1L, "SINGLE", "EASY", 10L),
      candidate(2L, "SINGLE", "EASY", 10L),
      candidate(3L, "SINGLE", "EASY", 10L));
    List<QuestionDrawEngine.Rule> rules = List.of(
      new QuestionDrawEngine.Rule(1, "SINGLE", "ALL", null, 2),
      new QuestionDrawEngine.Rule(2, "SINGLE", "ALL", null, 2));

    QuestionDrawEngine.Validation validation = QuestionDrawEngine.validate(pool, rules);

    assertThat(validation.executable()).isFalse();
    assertThat(validation.rules()).extracting(QuestionDrawEngine.RuleAvailability::availableCount)
      .containsExactly(2, 1);
  }

  @Test
  @DisplayName("难度和知识点共同过滤后的不足在对应规则中体现")
  void validationUsesDifficultyAndKnowledgePointFilters() {
    List<QuestionDrawEngine.Candidate> pool = List.of(
      candidate(1L, "SINGLE", "EASY", 10L),
      candidate(2L, "SINGLE", "HARD", 10L),
      candidate(3L, "SINGLE", "EASY", 20L));
    List<QuestionDrawEngine.Rule> rules = List.of(
      new QuestionDrawEngine.Rule(3, "SINGLE", "EASY", 10L, 2));

    QuestionDrawEngine.Validation validation = QuestionDrawEngine.validate(pool, rules);

    assertThat(validation.executable()).isFalse();
    assertThat(validation.rules()).singleElement().satisfies(item -> {
      assertThat(item.ruleIndex()).isEqualTo(3);
      assertThat(item.requiredCount()).isEqualTo(2);
      assertThat(item.availableCount()).isEqualTo(1);
    });
  }

  @Test
  @DisplayName("相同候选池、规则和种子生成相同题序")
  void drawIsReproducible() {
    List<QuestionDrawEngine.Candidate> pool = List.of(
      candidate(1L, "ESSAY", "NORMAL", null),
      candidate(2L, "ESSAY", "NORMAL", null),
      candidate(3L, "ESSAY", "NORMAL", null));
    List<QuestionDrawEngine.Rule> rules = List.of(
      new QuestionDrawEngine.Rule(1, "ESSAY", "ALL", null, 2));

    QuestionDrawEngine.DrawResult first = QuestionDrawEngine.draw(pool, rules, 99L);
    QuestionDrawEngine.DrawResult second = QuestionDrawEngine.draw(pool, rules, 99L);

    assertThat(second.assignments()).isEqualTo(first.assignments());
    assertThat(first.assignments()).allSatisfy(item ->
                                                 assertThat(item.evaluationMode()).isEqualTo(QuestionDrawEngine.EvaluationMode.RECORD_ONLY));
  }

  private QuestionDrawEngine.Candidate candidate(long id, String type, String difficulty, Long pointId) {
    QuestionDrawEngine.EvaluationMode mode = "ESSAY".equals(type)
      ? QuestionDrawEngine.EvaluationMode.RECORD_ONLY : QuestionDrawEngine.EvaluationMode.AUTO;
    return new QuestionDrawEngine.Candidate(id, 100L + id, type, difficulty,
                                            pointId == null ? Set.of() : Set.of(pointId), mode);
  }
}

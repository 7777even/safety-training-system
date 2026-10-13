package com.sts.exam.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sts.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuestionBankDrawEngineTest {

  @Test
  @DisplayName("恰好抽取规则数量，赋分合计为总分，相同种子可复现")
  void draw_usesSeedAndExactCounts() {
    List<QuestionBankDrawEngine.Candidate> pool = pool(12);
    List<QuestionBankDrawEngine.Rule> rules = List.of(
      new QuestionBankDrawEngine.Rule(1, "SINGLE", null, null, 10, new BigDecimal("10.00")));

    QuestionBankDrawEngine.DrawResult first = QuestionBankDrawEngine.draw(pool, rules, 42L);
    QuestionBankDrawEngine.DrawResult second = QuestionBankDrawEngine.draw(pool, rules, 42L);

    assertThat(first.questions()).hasSize(10);
    assertThat(first.totalScore()).isEqualByComparingTo("100.00");
    assertThat(first.seed()).isEqualTo(42L);
    assertThat(first.questions()).allMatch(q -> q.score().compareTo(new BigDecimal("10.00")) == 0);
    assertThat(second.questions().stream().map(QuestionBankDrawEngine.DrawnQuestion::questionId).toList())
      .isEqualTo(first.questions().stream().map(QuestionBankDrawEngine.DrawnQuestion::questionId).toList());
  }

  @Test
  @DisplayName("候选不足时失败并定位规则")
  void draw_insufficientCandidates_locatesRule() {
    List<QuestionBankDrawEngine.Candidate> pool = pool(3);
    List<QuestionBankDrawEngine.Rule> rules = List.of(
      new QuestionBankDrawEngine.Rule(2, "SINGLE", null, null, 10, new BigDecimal("10")));

    assertThatThrownBy(() -> QuestionBankDrawEngine.draw(pool, rules, 1L))
      .isInstanceOf(BusinessException.class)
      .hasFieldOrPropertyWithValue("code", 7063)
      .hasMessageContaining("2")
      .hasMessageContaining("SINGLE");
  }

  @Test
  @DisplayName("同一题目不能因命中多条规则而重复进入")
  void draw_doesNotReuseQuestionAcrossRules() {
    List<QuestionBankDrawEngine.Candidate> pool = pool(5);
    List<QuestionBankDrawEngine.Rule> rules = List.of(
      new QuestionBankDrawEngine.Rule(1, "SINGLE", null, null, 3, new BigDecimal("2")),
      new QuestionBankDrawEngine.Rule(2, "SINGLE", null, null, 3, new BigDecimal("2")));

    assertThatThrownBy(() -> QuestionBankDrawEngine.draw(pool, rules, 7L))
      .isInstanceOf(BusinessException.class)
      .hasFieldOrPropertyWithValue("code", 7063)
      .hasMessageContaining("2");
  }

  @Test
  @DisplayName("按题型、难度和知识点过滤")
  void draw_filtersTypeDifficultyAndKnowledge() {
    List<QuestionBankDrawEngine.Candidate> pool = List.of(
      new QuestionBankDrawEngine.Candidate(1L, 11L, "SINGLE", "EASY", Set.of(8L)),
      new QuestionBankDrawEngine.Candidate(2L, 12L, "SINGLE", "HARD", Set.of(8L)),
      new QuestionBankDrawEngine.Candidate(3L, 13L, "MULTIPLE", "EASY", Set.of(8L)),
      new QuestionBankDrawEngine.Candidate(4L, 14L, "SINGLE", "EASY", Set.of(9L)));
    List<QuestionBankDrawEngine.Rule> rules = List.of(
      new QuestionBankDrawEngine.Rule(1, "SINGLE", "EASY", 8L, 1, new BigDecimal("5")));

    QuestionBankDrawEngine.DrawResult result = QuestionBankDrawEngine.draw(pool, rules, 3L);

    assertThat(result.questions()).extracting(QuestionBankDrawEngine.DrawnQuestion::questionId)
      .containsExactly(1L);
  }

  @Test
  @DisplayName("从执行策略 JSON 解析冻结抽题规则")
  void parseRules_fromPolicySnapshot() throws Exception {
    String json = new ObjectMapper().writeValueAsString(java.util.Map.of(
      "drawRules", List.of(java.util.Map.of(
        "questionType", "SINGLE",
        "drawCount", 10,
        "scorePerQuestion", 10,
        "sortOrder", 1))));

    List<QuestionBankDrawEngine.Rule> rules = QuestionBankDrawEngine.parseRules(json, new ObjectMapper());

    assertThat(rules).hasSize(1);
    assertThat(rules.getFirst().drawCount()).isEqualTo(10);
    assertThat(rules.getFirst().scorePerQuestion()).isEqualByComparingTo("10");
  }

  @Test
  @DisplayName("新题库练习规则省略历史分值字段时仍可解析")
  void parseRules_allowsMissingLegacyScore() {
    String json = "{\"drawRules\":[{\"questionType\":\"ESSAY\",\"drawCount\":2}]}";

    List<QuestionBankDrawEngine.Rule> rules = QuestionBankDrawEngine.parseRules(json, new ObjectMapper());

    assertThat(rules).singleElement().satisfies(rule -> {
      assertThat(rule.questionType()).isEqualTo("ESSAY");
      assertThat(rule.scorePerQuestion()).isEqualByComparingTo(BigDecimal.ZERO);
    });
  }

  @Test
  @DisplayName("考试自身落库的裸规则数组与策略快照包裹结构解析结果一致")
  void parseRules_fromBareRuleArray() {
    String json = "[{\"questionType\":\"SINGLE\",\"drawCount\":3,\"scorePerQuestion\":4},"
      + "{\"questionType\":\"MULTIPLE\",\"drawCount\":2,\"scorePerQuestion\":4}]";

    List<QuestionBankDrawEngine.Rule> rules = QuestionBankDrawEngine.parseRules(json, new ObjectMapper());

    assertThat(rules).hasSize(2);
    assertThat(rules.get(0).questionType()).isEqualTo("SINGLE");
    assertThat(rules.get(1).drawCount()).isEqualTo(2);
    assertThat(QuestionBankDrawEngine.totalScore(rules)).isEqualByComparingTo("20.00");
  }

  @Test
  @DisplayName("空值、非数组与缺失 drawRules 键均返回空规则而不抛异常")
  void parseRules_returnsEmptyForUnusableInput() {
    assertThat(QuestionBankDrawEngine.parseRules(null, new ObjectMapper())).isEmpty();
    assertThat(QuestionBankDrawEngine.parseRules("  ", new ObjectMapper())).isEmpty();
    assertThat(QuestionBankDrawEngine.parseRules("[]", new ObjectMapper())).isEmpty();
    assertThat(QuestionBankDrawEngine.parseRules("{}", new ObjectMapper())).isEmpty();
    assertThat(QuestionBankDrawEngine.parseRules("\"x\"", new ObjectMapper())).isEmpty();
  }

  private List<QuestionBankDrawEngine.Candidate> pool(int size) {
    List<QuestionBankDrawEngine.Candidate> pool = new ArrayList<>();
    for (int i = 1; i <= size; i++) {
      pool.add(new QuestionBankDrawEngine.Candidate((long) i, 100L + i, "SINGLE", "EASY", Set.of()));
    }
    return pool;
  }
}

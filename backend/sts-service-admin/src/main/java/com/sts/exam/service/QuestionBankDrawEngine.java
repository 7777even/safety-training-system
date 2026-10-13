package com.sts.exam.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sts.common.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * 按冻结抽题规则从题库候选中抽取可复现的 Attempt 题目。
 */
public final class QuestionBankDrawEngine {

  private QuestionBankDrawEngine() {
  }

  public record Candidate(long questionId, long questionVersionId, String questionType,
                          String difficulty, Set<Long> knowledgePointIds,
                          com.sts.question.draw.QuestionDrawEngine.EvaluationMode evaluationMode) {
    public Candidate(long questionId, long questionVersionId, String questionType,
                     String difficulty, Set<Long> knowledgePointIds) {
      this(questionId, questionVersionId, questionType, difficulty, knowledgePointIds,
           "ESSAY".equals(questionType)
             ? com.sts.question.draw.QuestionDrawEngine.EvaluationMode.RECORD_ONLY
             : com.sts.question.draw.QuestionDrawEngine.EvaluationMode.AUTO);
    }
  }

  public record Rule(int index, String questionType, String difficulty, Long knowledgePointId,
                     int drawCount, BigDecimal scorePerQuestion) {
  }

  public record DrawnQuestion(long questionId, long questionVersionId, String questionType,
                              BigDecimal score, int sortOrder, int ruleIndex,
                              com.sts.question.draw.QuestionDrawEngine.EvaluationMode evaluationMode) {
  }

  public record DrawResult(long seed, BigDecimal totalScore, List<DrawnQuestion> questions) {
  }

  /**
   * 解析抽题规则，兼容两种落库形态：
   * <ul>
   *   <li>策略快照 {@code {"drawRules":[...]}} —— 培训来源与独立考试发布后的 {@code policy_snapshot_json}；</li>
   *   <li>裸规则数组 {@code [...]} —— 考试自身的 {@code sts_exam_exam.draw_rule_json} 列。</li>
   * </ul>
   * 两种形态共用一个解析入口，避免规则语义分叉。
   */
  public static List<Rule> parseRules(String policySnapshotJson, ObjectMapper objectMapper) {
    if (!StringUtils.hasText(policySnapshotJson) || objectMapper == null) {
      return List.of();
    }
    try {
      JsonNode root = objectMapper.readTree(policySnapshotJson);
      JsonNode rulesNode = root.isArray() ? root : root.get("drawRules");
      if (rulesNode == null || !rulesNode.isArray() || rulesNode.isEmpty()) {
        return List.of();
      }
      List<Rule> rules = new ArrayList<>();
      int index = 1;
      for (JsonNode node : rulesNode) {
        String questionType = text(node, "questionType");
        int drawCount = node.path("drawCount").asInt(0);
        BigDecimal score = node.hasNonNull("scorePerQuestion")
          ? new BigDecimal(node.get("scorePerQuestion").asText()) : BigDecimal.ZERO;
        if (!StringUtils.hasText(questionType) || drawCount <= 0) {
          throw new BusinessException(HttpStatus.BAD_REQUEST, 7063,
                                      "抽题规则[" + index + "]不完整");
        }
        Long knowledgePointId = node.hasNonNull("knowledgePointId")
          ? node.get("knowledgePointId").asLong() : null;
        String difficulty = text(node, "difficulty");
        rules.add(new Rule(index++, questionType, difficulty, knowledgePointId, drawCount, score));
      }
      return rules;
    } catch (BusinessException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 7063, "冻结抽题规则无法解析");
    }
  }

  public static DrawResult draw(List<Candidate> pool, List<Rule> rules, long seed) {
    if (rules == null || rules.isEmpty()) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 7063, "题库练习没有冻结抽题规则");
    }
    List<com.sts.question.draw.QuestionDrawEngine.Candidate> commonPool = pool.stream()
      .map(candidate -> new com.sts.question.draw.QuestionDrawEngine.Candidate(
        candidate.questionId(), candidate.questionVersionId(), candidate.questionType(),
        candidate.difficulty(), candidate.knowledgePointIds(), candidate.evaluationMode()))
      .toList();
    List<com.sts.question.draw.QuestionDrawEngine.Rule> commonRules = rules.stream()
      .map(rule -> new com.sts.question.draw.QuestionDrawEngine.Rule(
        rule.index(), rule.questionType(), rule.difficulty(), rule.knowledgePointId(), rule.drawCount()))
      .toList();
    com.sts.question.draw.QuestionDrawEngine.DrawResult commonResult;
    try {
      commonResult = com.sts.question.draw.QuestionDrawEngine.draw(commonPool, commonRules, seed);
    } catch (com.sts.question.draw.QuestionDrawEngine.InsufficientCandidatesException ex) {
      com.sts.question.draw.QuestionDrawEngine.RuleAvailability shortage = ex.validation().rules().stream()
        .filter(item -> !item.sufficient()).findFirst().orElse(null);
      int ruleIndex = shortage == null ? 1 : shortage.ruleIndex();
      Rule rule = rules.stream().filter(item -> item.index() == ruleIndex).findFirst().orElse(rules.get(0));
      throw new BusinessException(HttpStatus.BAD_REQUEST, 7063,
                                  "抽题规则[" + rule.index() + "] " + rule.questionType() + " 候选不足");
    }
    Map<Integer, Rule> rulesByIndex = new HashMap<>();
    rules.forEach(rule -> rulesByIndex.put(rule.index(), rule));
    List<DrawnQuestion> drawn = new ArrayList<>();
    BigDecimal total = BigDecimal.ZERO;
    for (com.sts.question.draw.QuestionDrawEngine.Assignment item : commonResult.assignments()) {
      Rule rule = rulesByIndex.get(item.ruleIndex());
      BigDecimal score = rule.scorePerQuestion().setScale(2, RoundingMode.HALF_UP);
      drawn.add(new DrawnQuestion(item.questionId(), item.questionVersionId(),
                                  item.questionType(), score, item.sortOrder(), item.ruleIndex(),
                                  item.evaluationMode()));
      total = total.add(score);
    }
    return new DrawResult(seed, total.setScale(2, RoundingMode.HALF_UP), List.copyOf(drawn));
  }

  public static BigDecimal totalScore(List<Rule> rules) {
    BigDecimal total = BigDecimal.ZERO;
    if (rules == null) {
      return total;
    }
    for (Rule rule : rules) {
      total = total.add(rule.scorePerQuestion().multiply(BigDecimal.valueOf(rule.drawCount())));
    }
    return total.setScale(2, RoundingMode.HALF_UP);
  }

  private static String text(JsonNode node, String field) {
    JsonNode value = node.get(field);
    return value == null || value.isNull() ? null : value.asText();
  }
}

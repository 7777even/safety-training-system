package com.sts.question.draw;

import java.util.*;

/**
 * 场景无关的题目抽取引擎。只处理候选过滤、全局去重匹配和随机顺序，不感知培训、考试或分值。
 */
public final class QuestionDrawEngine {

  private QuestionDrawEngine() {
  }

  /**
   * 题目作答后的判定能力。
   */
  public enum EvaluationMode {
    AUTO,
    RECORD_ONLY,
    UNSUPPORTED
  }

  /**
   * 可参与抽取的不可变题目版本。
   */
  public record Candidate(long questionId, long questionVersionId, String questionType,
                          String difficulty, Set<Long> knowledgePointIds,
                          EvaluationMode evaluationMode) {
    public Candidate {
      knowledgePointIds = knowledgePointIds == null ? Set.of() : Set.copyOf(knowledgePointIds);
    }
  }

  /**
   * 一条抽题规则，index 是调用方用于定位规则的稳定序号。
   */
  public record Rule(int index, String questionType, String difficulty,
                     Long knowledgePointId, int drawCount) {
  }

  /**
   * 一条规则在全局题目唯一约束下实际可分配的数量。
   */
  public record RuleAvailability(int ruleIndex, int requiredCount, int availableCount) {
    public boolean sufficient() {
      return availableCount >= requiredCount;
    }
  }

  /**
   * 对整组规则执行全局匹配后的可执行性。
   */
  public record Validation(boolean executable, List<RuleAvailability> rules) {
    public Validation {
      rules = List.copyOf(rules);
    }
  }

  /**
   * 抽取结果中的题目及其规则归属。
   */
  public record Assignment(int ruleIndex, long questionId, long questionVersionId,
                           String questionType, EvaluationMode evaluationMode, int sortOrder) {
  }

  /**
   * 可复现的完整抽题结果。
   */
  public record DrawResult(long seed, List<Assignment> assignments) {
    public DrawResult {
      assignments = List.copyOf(assignments);
    }
  }

  /**
   * 候选不足时保留规则级诊断，供不同业务场景转换为自己的错误契约。
   */
  public static final class InsufficientCandidatesException extends IllegalArgumentException {
    private final Validation validation;

    public InsufficientCandidatesException(Validation validation) {
      super("question draw candidates are insufficient");
      this.validation = validation;
    }

    public Validation validation() {
      return validation;
    }
  }

  public static Validation validate(List<Candidate> pool, List<Rule> rules) {
    MatchResult result = match(pool, rules, null);
    List<RuleAvailability> availability = availability(rules, result);
    return new Validation(availability.stream().allMatch(RuleAvailability::sufficient), availability);
  }

  public static DrawResult draw(List<Candidate> pool, List<Rule> rules, long seed) {
    MatchResult result = match(pool, rules, seed);
    List<RuleAvailability> availability = availability(rules, result);
    Validation validation = new Validation(
      availability.stream().allMatch(RuleAvailability::sufficient), availability);
    if (!validation.executable()) {
      throw new InsufficientCandidatesException(validation);
    }
    List<Slot> outputSlots = new ArrayList<>(result.slotAssignments.keySet());
    outputSlots.sort(Comparator.comparingInt(Slot::rulePosition).thenComparingInt(Slot::slotPosition));
    List<Assignment> assignments = new ArrayList<>();
    int sortOrder = 1;
    for (Slot slot : outputSlots) {
      Candidate candidate = result.slotAssignments.get(slot);
      assignments.add(new Assignment(slot.rule.index(), candidate.questionId(),
                                     candidate.questionVersionId(), candidate.questionType(),
                                     candidate.evaluationMode(), sortOrder++));
    }
    return new DrawResult(seed, assignments);
  }

  private static MatchResult match(List<Candidate> pool, List<Rule> rules, Long seed) {
    List<Rule> normalizedRules = rules == null ? List.of() : List.copyOf(rules);
    validateRules(normalizedRules);
    List<Candidate> candidates = normalizeCandidates(pool, seed);
    List<Slot> slots = new ArrayList<>();
    for (int rulePosition = 0; rulePosition < normalizedRules.size(); rulePosition++) {
      Rule rule = normalizedRules.get(rulePosition);
      int eligibleCount = (int) candidates.stream().filter(candidate -> matches(candidate, rule)).count();
      for (int slotPosition = 0; slotPosition < rule.drawCount(); slotPosition++) {
        slots.add(new Slot(rulePosition, slotPosition, eligibleCount, rule));
      }
    }
    // 先分配候选范围更窄的槽位，随后用增广路径保证得到全局最大匹配。
    slots.sort(Comparator.comparingInt(Slot::eligibleCount)
                 .thenComparingInt(Slot::rulePosition)
                 .thenComparingInt(Slot::slotPosition));
    Map<Long, Slot> candidateAssignments = new HashMap<>();
    Map<Slot, Candidate> slotAssignments = new HashMap<>();
    for (Slot slot : slots) {
      assign(slot, candidates, candidateAssignments, slotAssignments, new HashSet<>());
    }
    return new MatchResult(slotAssignments);
  }

  private static boolean assign(Slot slot, List<Candidate> candidates,
                                Map<Long, Slot> candidateAssignments,
                                Map<Slot, Candidate> slotAssignments, Set<Long> visited) {
    for (Candidate candidate : candidates) {
      if (!matches(candidate, slot.rule()) || !visited.add(candidate.questionId())) {
        continue;
      }
      Slot occupied = candidateAssignments.get(candidate.questionId());
      if (occupied == null
        || assign(occupied, candidates, candidateAssignments, slotAssignments, visited)) {
        candidateAssignments.put(candidate.questionId(), slot);
        slotAssignments.put(slot, candidate);
        return true;
      }
    }
    return false;
  }

  private static List<Candidate> normalizeCandidates(List<Candidate> pool, Long seed) {
    LinkedHashMap<Long, Candidate> unique = new LinkedHashMap<>();
    if (pool != null) {
      pool.stream().filter(Objects::nonNull)
        .sorted(Comparator.comparingLong(Candidate::questionId)
                  .thenComparingLong(Candidate::questionVersionId))
        .forEach(candidate -> unique.putIfAbsent(candidate.questionId(), candidate));
    }
    List<Candidate> candidates = new ArrayList<>(unique.values());
    if (seed != null) {
      Collections.shuffle(candidates, new Random(seed));
    }
    return candidates;
  }

  private static List<RuleAvailability> availability(List<Rule> rules, MatchResult result) {
    List<RuleAvailability> availability = new ArrayList<>();
    for (int position = 0; position < rules.size(); position++) {
      int assigned = 0;
      for (Slot slot : result.slotAssignments.keySet()) {
        if (slot.rulePosition() == position) {
          assigned++;
        }
      }
      Rule rule = rules.get(position);
      availability.add(new RuleAvailability(rule.index(), rule.drawCount(), assigned));
    }
    return availability;
  }

  private static boolean matches(Candidate candidate, Rule rule) {
    if (!Objects.equals(rule.questionType(), candidate.questionType())) {
      return false;
    }
    if (hasText(rule.difficulty()) && !"ALL".equals(rule.difficulty())
      && !Objects.equals(rule.difficulty(), candidate.difficulty())) {
      return false;
    }
    return rule.knowledgePointId() == null || rule.knowledgePointId() == 0L
      || candidate.knowledgePointIds().contains(rule.knowledgePointId());
  }

  private static void validateRules(List<Rule> rules) {
    Set<Integer> indexes = new HashSet<>();
    if (rules.isEmpty()) {
      throw new IllegalArgumentException("question draw rules are required");
    }
    for (Rule rule : rules) {
      if (rule == null || !hasText(rule.questionType()) || rule.drawCount() <= 0
        || !indexes.add(rule.index())) {
        throw new IllegalArgumentException("invalid question draw rule");
      }
    }
  }

  private static boolean hasText(String value) {
    return value != null && !value.trim().isEmpty();
  }

  private record Slot(int rulePosition, int slotPosition, int eligibleCount, Rule rule) {
  }

  private record MatchResult(Map<Slot, Candidate> slotAssignments) {
  }
}

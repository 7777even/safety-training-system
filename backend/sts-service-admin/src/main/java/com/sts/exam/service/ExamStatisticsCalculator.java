package com.sts.exam.service;

import com.sts.exam.entity.*;
import com.sts.exam.vo.ExamStatisticsVO;
import com.sts.exam.vo.ExamStatisticsVO.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.function.Function;

/** 纯计算考情口径，不读取数据库、不回写正式成绩。 */
public final class ExamStatisticsCalculator {
  private ExamStatisticsCalculator() {}
  /** 百分数，零分母为零。 */
  public static BigDecimal percent(long numerator, long denominator) {
    return denominator == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(numerator * 100).divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
  }
  /** 仅正式作答参与考情，练习和模拟排除。 */
  public static boolean formal(ExamAttempt a) { return "FORMAL_EXAM".equals(a.getAssessmentKind()); }
  /** 成绩发布后才进入成绩分布及排名。 */
  public static boolean scored(ExamCandidate c) { return c.getResultPublishedTime() != null && c.getFinalScore() != null && c.getFinalAttemptId() != null; }
  /** 并列同名次，后续名次跳号。 */
  public static Map<Long,Integer> ranks(List<ExamCandidate> candidates) {
    var sorted = candidates.stream().filter(ExamStatisticsCalculator::scored)
      .sorted(Comparator.comparing(ExamCandidate::getFinalScore).reversed().thenComparing(ExamCandidate::getExamCandidateId)).toList();
    Map<Long,Integer> ranks = new HashMap<>();
    BigDecimal previous = null; int rank = 0;
    for (int i = 0; i < sorted.size(); i++) {
      var c = sorted.get(i);
      if (previous == null || previous.compareTo(c.getFinalScore()) != 0) rank = i + 1;
      ranks.put(c.getExamCandidateId(), rank); previous = c.getFinalScore();
    }
    return ranks;
  }
  /** 按最终答卷分析题目，按最近正式作答分析客户端。 */
  public static ExamStatisticsVO calculate(List<ExamCandidate> candidates, List<ExamAttempt> attempts,
      List<ExamAttemptQuestion> questions, List<ExamAnswer> answers, boolean closed) {
    Set<Long> candidateIds = new HashSet<>(); candidates.forEach(c -> candidateIds.add(c.getExamCandidateId()));
    var formalAttempts = attempts.stream().filter(ExamStatisticsCalculator::formal).filter(a -> candidateIds.contains(a.getExamCandidateId())).toList();
    Map<Long,ExamAttempt> latest = new HashMap<>();
    for (var a : formalAttempts) latest.merge(a.getExamCandidateId(), a, (left,right) -> left.getExamAttemptId() > right.getExamAttemptId() ? left : right);
    long completed = formalAttempts.stream().filter(a -> a.getSubmittedTime() != null).map(ExamAttempt::getExamCandidateId).distinct().count();
    long pending = formalAttempts.stream().filter(a -> "PENDING_REVIEW".equals(a.getAttemptStatus())).map(ExamAttempt::getExamCandidateId).distinct().count();
    var scored = candidates.stream().filter(ExamStatisticsCalculator::scored).toList();
    Set<Long> selected = new HashSet<>(); scored.forEach(c -> selected.add(c.getFinalAttemptId()));
    Map<Long,ExamAttempt> byId = new HashMap<>(); formalAttempts.forEach(a -> byId.put(a.getExamAttemptId(), a));
    List<Bucket> bands = new ArrayList<>();
    long[] counts = new long[5]; String[] labels = {"0–<60","60–<70","70–<80","80–<90","90–100"};
    for (var c : scored) {
      var a = byId.get(c.getFinalAttemptId());
      if (a == null || a.getTotalScoreSnapshot() == null || a.getTotalScoreSnapshot().signum() <= 0) continue;
      var normalized = c.getFinalScore().multiply(BigDecimal.valueOf(100)).divide(a.getTotalScoreSnapshot(), 4, RoundingMode.HALF_UP);
      int index = normalized.compareTo(BigDecimal.valueOf(60)) < 0 ? 0 : normalized.compareTo(BigDecimal.valueOf(70)) < 0 ? 1 :
        normalized.compareTo(BigDecimal.valueOf(80)) < 0 ? 2 : normalized.compareTo(BigDecimal.valueOf(90)) < 0 ? 3 : 4;
      counts[index]++;
    }
    long bandTotal = Arrays.stream(counts).sum();
    for (int i = 0; i < 5; i++) bands.add(new Bucket(labels[i], counts[i], percent(counts[i],bandTotal)));
    var selectedQuestions = questions.stream().filter(q -> selected.contains(q.getExamAttemptId())).toList();
    Map<Long,ExamAnswer> answerMap = new HashMap<>();
    answers.stream().filter(a -> selected.contains(a.getExamAttemptId())).forEach(a -> answerMap.put(a.getAttemptQuestionId(),a));
    Map<String,List<ExamAttemptQuestion>> types = new TreeMap<>();
    selectedQuestions.forEach(q -> types.computeIfAbsent(q.getQuestionType(),k -> new ArrayList<>()).add(q));
    List<QuestionType> typeStats = new ArrayList<>();
    types.forEach((type,qs) -> {
      var evaluated = qs.stream().map(q -> answerMap.get(q.getAttemptQuestionId())).filter(Objects::nonNull).filter(a -> a.getIsCorrect() != null).toList();
      long correct = evaluated.stream().filter(a -> a.getIsCorrect() == 1).count();
      typeStats.add(new QuestionType(type,qs.size(),percent(qs.size(),selectedQuestions.size()),evaluated.size(),correct,
        evaluated.isEmpty() ? null : percent(correct,evaluated.size())));
    });
    Map<Long,List<ExamAttemptQuestion>> versions = new TreeMap<>();
    selectedQuestions.forEach(q -> versions.computeIfAbsent(q.getQuestionVersionId(), k -> new ArrayList<>()).add(q));
    List<WrongQuestion> wrong = new ArrayList<>();
    versions.forEach((version,qs) -> {
      Set<Long> wrongPeople = new HashSet<>(), evaluatedPeople = new HashSet<>();
      for (var q : qs) {
        var answer = answerMap.get(q.getAttemptQuestionId()); var a = byId.get(q.getExamAttemptId());
        if (answer == null || answer.getIsCorrect() == null || a == null) continue;
        evaluatedPeople.add(a.getExamCandidateId());
        if (answer.getIsCorrect() == 0) wrongPeople.add(a.getExamCandidateId());
      }
      if (!wrongPeople.isEmpty()) wrong.add(new WrongQuestion(version,qs.get(0).getStemSnapshot(),wrongPeople.size(),percent(wrongPeople.size(),evaluatedPeople.size())));
    });
    var scores = scored.stream().map(ExamCandidate::getFinalScore).toList();
    var durations = latest.values().stream().map(ExamAttempt::getUsedSeconds).filter(Objects::nonNull).map(BigDecimal::valueOf).toList();
    return new ExamStatisticsVO(candidates.size(),latest.size(),(int)completed,percent(completed,candidates.size()),(int)pending,
      (int)candidates.stream().filter(c -> !scored(c) && formalAttempts.stream().anyMatch(a -> Objects.equals(a.getExamCandidateId(),c.getExamCandidateId()) && a.getSubmittedTime()!=null)).count(),
      closed ? candidates.size()-latest.size() : 0,
      scores.stream().max(BigDecimal::compareTo).orElse(null),average(scores),scores.stream().min(BigDecimal::compareTo).orElse(null),
      bands,typeStats,wrong.stream().sorted(Comparator.comparingLong(WrongQuestion::wrongCount).reversed().thenComparing(WrongQuestion::questionVersionId)).limit(5).toList(),
      buckets(latest.values(),ExamAttempt::getPlatform),buckets(latest.values(),ExamAttempt::getOperatingSystem),buckets(latest.values(),ExamAttempt::getBrowser),average(durations));
  }
  private static BigDecimal average(List<BigDecimal> values) {
    return values.isEmpty() ? null : values.stream().reduce(BigDecimal.ZERO,BigDecimal::add).divide(BigDecimal.valueOf(values.size()),2,RoundingMode.HALF_UP);
  }
  private static List<Bucket> buckets(Collection<ExamAttempt> attempts, Function<ExamAttempt,String> getter) {
    Map<String,Long> counts = new TreeMap<>();
    attempts.forEach(a -> { var value = getter.apply(a); counts.merge(value == null || value.isBlank() ? "未知" : value,1L,Long::sum); });
    return counts.entrySet().stream().map(e -> new Bucket(e.getKey(),e.getValue(),percent(e.getValue(),attempts.size()))).toList();
  }
}

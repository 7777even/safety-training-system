package com.sts.exam.vo;

import java.math.BigDecimal;
import java.util.List;

/** 当次考试的人员去重统计；比例均为百分数，空成绩不冒充零分。 */
public record ExamStatisticsVO(
  int expectedCount, int attendedCount, int completedCount, BigDecimal completionRate,
  int pendingReviewCount, int unpublishedCount, int absentCount,
  BigDecimal highestScore, BigDecimal averageScore, BigDecimal lowestScore,
  List<Bucket> scoreBands, List<QuestionType> questionTypes, List<WrongQuestion> wrongTop5,
  List<Bucket> platforms, List<Bucket> operatingSystems, List<Bucket> browsers,
  BigDecimal averageUsedSeconds) {
  /** 分类人数及其占比。 */
  public record Bucket(String label, long count, BigDecimal percent) {}
  /** 题型数量占比及已判定题正确率。 */
  public record QuestionType(String questionType, long count, BigDecimal percent, long evaluatedCount,
                             long correctCount, BigDecimal accuracyRate) {}
  /** 按冻结题目版本汇总的错误人数与错误率。 */
  public record WrongQuestion(Long questionVersionId, String stem, long wrongCount, BigDecimal wrongRate) {}
}

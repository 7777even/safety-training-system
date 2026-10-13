package com.sts.course.service;

import com.sts.course.entity.CourseEnrollment;
import com.sts.course.entity.CourseOffering;
import com.sts.course.snapshot.CourseContentSnapshot;
import com.sts.course.snapshot.ItemSnapshot;
import com.sts.exam.entity.ExamCandidate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 培训课程正式成绩的唯一计算口径，供实时重算与历史补账共同调用。 */
final class TrainingCourseScoreCalculator {

  private TrainingCourseScoreCalculator() {
  }

  /** 仅培训绑定且已完成的课程，按完成规则范围内课程测验的当前正式分取平均。 */
  static BigDecimal calculate(CourseEnrollment enrollment, CourseOffering offering,
                              CourseContentSnapshot snapshot, List<ExamCandidate> candidates) {
    if (enrollment.getTrainingParticipantId() == null || offering.getPlanContentId() == null
      || !"COMPLETED".equals(enrollment.getLearningStatus())) {
      return null;
    }
    List<Long> quizItemIds = relevantItems(snapshot, offering.getCompletionRule()).stream()
      .filter(item -> "QUIZ".equals(item.getItemType()))
      .map(ItemSnapshot::getCourseItemId)
      .toList();
    if (quizItemIds.isEmpty()) {
      return null;
    }
    Map<Long, BigDecimal> scores = candidates.stream()
      .filter(candidate -> candidate.getCourseItemId() != null && candidate.getFinalScore() != null)
      .collect(Collectors.toMap(ExamCandidate::getCourseItemId, ExamCandidate::getFinalScore, (first, ignored) -> first));
    if (quizItemIds.stream().anyMatch(itemId -> !scores.containsKey(itemId))) {
      return null;
    }
    return quizItemIds.stream().map(scores::get).reduce(BigDecimal.ZERO, BigDecimal::add)
      .divide(BigDecimal.valueOf(quizItemIds.size()), 2, RoundingMode.HALF_UP);
  }

  /** 按冻结开课的完成规则确定参与课程成绩的内容项范围。 */
  private static List<ItemSnapshot> relevantItems(CourseContentSnapshot snapshot, String completionRule) {
    List<ItemSnapshot> items = snapshot.getChapters() == null ? List.of() : snapshot.getChapters().stream()
      .filter(chapter -> chapter.getItems() != null)
      .flatMap(chapter -> chapter.getItems().stream())
      .toList();
    if ("ALL_ITEMS".equals(completionRule)) {
      return items;
    }
    return items.stream().filter(item -> item.getIsRequired() != null && item.getIsRequired() == 1).toList();
  }
}

package com.sts.course.service;

import com.sts.course.entity.CourseEnrollment;
import com.sts.course.entity.CourseOffering;
import com.sts.course.snapshot.ChapterSnapshot;
import com.sts.course.snapshot.CourseContentSnapshot;
import com.sts.course.snapshot.ItemSnapshot;
import com.sts.exam.entity.ExamCandidate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TrainingCourseScoreCalculatorTest {

  @Test
  @DisplayName("培训课程只平均完成范围内的课程测验")
  void calculate_averagesRequiredQuizzesOnly() {
    assertThat(TrainingCourseScoreCalculator.calculate(trainingEnrollment(), trainingOffering(), snapshot(), List.of(
      candidate(101L, "80"), candidate(102L, "90"), candidate(103L, "100"), candidate(104L, "100"))))
      .isEqualByComparingTo("85.00");
  }

  @Test
  @DisplayName("0 分参与培训课程平均分")
  void calculate_includesZeroScore() {
    assertThat(TrainingCourseScoreCalculator.calculate(trainingEnrollment(), trainingOffering(), snapshot(), List.of(
      candidate(101L, "0"), candidate(102L, "90"))))
      .isEqualByComparingTo("45.00");
  }

  @Test
  @DisplayName("独立课程不产生培训课程正式成绩")
  void calculate_returnsNullForIndependentCourse() {
    CourseEnrollment enrollment = trainingEnrollment();
    enrollment.setTrainingParticipantId(null);

    assertThat(TrainingCourseScoreCalculator.calculate(enrollment, trainingOffering(), snapshot(), List.of(
      candidate(101L, "80"), candidate(102L, "90")))).isNull();
  }

  /** 构造培训绑定、已完成的课程报名。 */
  private CourseEnrollment trainingEnrollment() {
    CourseEnrollment enrollment = new CourseEnrollment();
    enrollment.setTrainingParticipantId(20L);
    enrollment.setLearningStatus("COMPLETED");
    return enrollment;
  }

  /** 构造培训计划内容生成的开课。 */
  private CourseOffering trainingOffering() {
    CourseOffering offering = new CourseOffering();
    offering.setPlanContentId(30L);
    offering.setCompletionRule("ALL_REQUIRED_ITEMS");
    return offering;
  }

  /** 构造两个必修测验、一个选修测验和一个题库练习。 */
  private CourseContentSnapshot snapshot() {
    ChapterSnapshot chapter = new ChapterSnapshot();
    chapter.setItems(List.of(item(101L, "QUIZ", 1), item(102L, "QUIZ", 1),
      item(103L, "QUIZ", 0), item(104L, "QUESTION_BANK", 1)));
    CourseContentSnapshot snapshot = new CourseContentSnapshot();
    snapshot.setChapters(List.of(chapter));
    return snapshot;
  }

  /** 构造冻结内容项。 */
  private ItemSnapshot item(Long courseItemId, String itemType, Integer isRequired) {
    ItemSnapshot item = new ItemSnapshot();
    item.setCourseItemId(courseItemId);
    item.setItemType(itemType);
    item.setIsRequired(isRequired);
    return item;
  }

  /** 构造课程测验的当前正式分。 */
  private ExamCandidate candidate(Long courseItemId, String score) {
    ExamCandidate candidate = new ExamCandidate();
    candidate.setCourseItemId(courseItemId);
    candidate.setFinalScore(new BigDecimal(score));
    return candidate;
  }
}

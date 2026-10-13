package com.sts.exam.service;

import cn.dev33.satoken.stp.StpUtil;
import com.sts.common.api.*;
import com.sts.course.entity.CourseEnrollment;
import com.sts.course.mapper.CourseEnrollmentMapper;
import com.sts.course.service.CourseLearningService;
import com.sts.course.vo.*;
import com.sts.exam.vo.ExamLearnerTaskVO;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

/** 没有正式考试人员时，课程随堂测试仍应出现在我的考试。 */
class LearnerAssessmentCenterServiceTest {
  /** 独立练习已安排给本人时首页保留原入口，原正式考试中心范围不变。 */
  @Test void homeIncludesAssignedPracticeWithOriginalAction() {
    var exams = mock(ExamLearnerService.class); var courses = mock(CourseLearningService.class);
    var enrollments = mock(CourseEnrollmentMapper.class);
    var task = new ExamLearnerTaskVO(); task.setExamType("BANK_PRACTICE"); task.setExamCandidateId(11L);
    task.setExamId(12L); task.setExamName("题库练习"); task.setTaskStatus("NOT_STARTED"); task.setAvailableAction("START");
    when(exams.pageMyTasks(any())).thenReturn(new PageResult<>(List.of(task), 1));
    when(enrollments.selectList(any())).thenReturn(List.of());
    var service = new LearnerAssessmentCenterService(exams, courses, enrollments, null, null, null);
    try (var stp = mockStatic(StpUtil.class)) {
      stp.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
      assertThat(service.tasks(new PageRequest<>(1, 20, null)).list()).isEmpty();
      var home = service.listHomeTasks();
      assertThat(home).hasSize(1); assertThat(home.getFirst().action()).isEqualTo("START");
      assertThat(home.getFirst().candidateId()).isEqualTo(11L);
      assertThat(home.getFirst().examId()).isEqualTo(12L);
    }
  }
  @Test void includesUnstartedCourseQuizWithoutExamCandidate() {
    var exams = mock(ExamLearnerService.class); var courses = mock(CourseLearningService.class);
    var enrollments = mock(CourseEnrollmentMapper.class);
    when(exams.pageMyTasks(any())).thenReturn(new PageResult<>(List.of(), 0));
    var enrollment = new CourseEnrollment(); enrollment.setCourseEnrollmentId(3L); enrollment.setUserId(7L);
    when(enrollments.selectList(any())).thenReturn(List.of(enrollment));
    var quiz = new LearnerCourseTaskItemVO(); quiz.setItemType("QUIZ"); quiz.setCourseItemId(4L);
    quiz.setItemTitle("随堂测试"); quiz.setExamTaskStatus("NOT_STARTED"); quiz.setLearnable(true);
    var chapter = new LearnerCourseTaskChapterVO(); chapter.setItems(List.of(quiz));
    var tasks = new LearnerCourseTasksVO(); tasks.setCourseName("安全课程"); tasks.setChapters(List.of(chapter));
    when(courses.getMyEnrollmentTasks(3L)).thenReturn(tasks);
    var service = new LearnerAssessmentCenterService(exams, courses, enrollments, null, null, null);
    try (var stp = mockStatic(StpUtil.class)) {
      stp.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
      var result = service.tasks(new PageRequest<>(1, 20, null));
      assertThat(result.total()).isEqualTo(1);
      assertThat(result.list().getFirst().kind()).isEqualTo("COURSE_QUIZ");
      assertThat(result.list().getFirst().enrollmentId()).isEqualTo(3L);
      assertThat(result.list().getFirst().itemId()).isEqualTo(4L);
      // 课程结束后不可继续学习，已发布结果仍须可回看。
      quiz.setLearnable(false); quiz.setExamTaskStatus("RESULT_PUBLISHED"); quiz.setPublishedExamAttemptId(9L);
      assertThat(service.tasks(new PageRequest<>(1, 20, null)).list().getFirst().available()).isTrue();
    }
  }
}

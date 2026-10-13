package com.sts.learner.service;

import cn.dev33.satoken.stp.StpUtil;
import com.sts.common.api.*;
import com.sts.course.service.CourseLearningService;
import com.sts.course.vo.LearnerCourseEnrollmentVO;
import com.sts.exam.service.LearnerAssessmentCenterService;
import com.sts.training.service.TrainingWorkflowService;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.stream.LongStream;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 首页通过本人领域接口读取，不能创造报名或伪造统计。 */
class LearnerHomeServiceTest {
  @Test void assignedCourseKeepsItsRealEnrollmentReference() {
    var courses = mock(CourseLearningService.class);
    var exams = mock(LearnerAssessmentCenterService.class);
    var training = mock(TrainingWorkflowService.class);
    var course = new LearnerCourseEnrollmentVO();
    course.setEnrollmentId(31L); course.setCourseName("安全课程"); course.setLearningStatus("LEARNING");
    when(courses.pageMyEnrollments(any())).thenReturn(new PageResult<>(List.of(course), 1));
    when(exams.listHomeTasks()).thenReturn(List.of());
    when(training.listLearnerTasks(7L)).thenReturn(List.of());
    try (var stp = mockStatic(StpUtil.class)) {
      stp.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
      var result = new LearnerHomeService(courses, exams, training).tasks(new PageRequest<>(1, 20, null));
      assertThat(result.total()).isEqualTo(1);
      assertThat(result.list().getFirst().kind()).isEqualTo("COURSE");
      assertThat(result.list().getFirst().resourceId()).isEqualTo(31L);
      assertThat(result.list().getFirst().title()).isEqualTo("安全课程");
    }
  }

  @Test void courseCategoryDoesNotReadUnrelatedAssessmentOrTraining() {
    var courses = mock(CourseLearningService.class);
    var exams = mock(LearnerAssessmentCenterService.class);
    var training = mock(TrainingWorkflowService.class);
    when(courses.pageMyEnrollments(any())).thenReturn(new PageResult<>(List.of(), 0));
    var query = new LearnerHomeService.Query(); query.setKind("COURSE");
    new LearnerHomeService(courses, exams, training).tasks(new PageRequest<>(1, 20, query));
    verifyNoInteractions(exams, training);
  }

  @Test void moreThanTwoHundredAssessmentTasksAreCollectedOnlyOnce() {
    var courses = mock(CourseLearningService.class);
    var exams = mock(LearnerAssessmentCenterService.class);
    var training = mock(TrainingWorkflowService.class);
    var items = LongStream.rangeClosed(1, 401).mapToObj(id -> new LearnerAssessmentCenterService.Task(
      "exam-" + id, "FORMAL_EXAM", "专项练习", "管理员安排", "SPECIAL_PRACTICE", "UNFINISHED",
      true, "START", id, 9L, null, null, null)).toList();
    when(exams.listHomeTasks()).thenReturn(items);
    var query = new LearnerHomeService.Query(); query.setKind("FORMAL_EXAM");
    var result = new LearnerHomeService(courses, exams, training).tasks(new PageRequest<>(2, 200, query));
    assertThat(result.total()).isEqualTo(401); assertThat(result.list()).hasSize(200);
    verify(exams, times(1)).listHomeTasks(); verifyNoInteractions(courses, training);
  }
}

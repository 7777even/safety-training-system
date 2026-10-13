package com.sts.course.service;

import com.sts.admin.mapper.SysUserMapper;
import com.sts.course.entity.CourseOffering;
import com.sts.course.mapper.CourseAssessmentResultMapper;
import com.sts.course.mapper.CourseMapper;
import com.sts.course.mapper.CourseOfferingMapper;
import com.sts.course.mapper.CourseVersionMapper;
import com.sts.exam.entity.AssessmentExecution;
import com.sts.exam.mapper.ExamAttemptQueryMapper;
import com.sts.exam.mapper.ExamReviewerMapper;
import com.sts.exam.mapper.ExamReviewerQueryMapper;
import com.sts.exam.service.AssessmentExecutionService;
import com.sts.exam.service.ExamAttemptService;
import com.sts.exam.vo.ExamReviewerVO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CourseAssessmentManagementServiceTest {

  @Test
  void listReviewers_resolvesOnlyTheCourseQuizExecution() {
    CourseOfferingMapper offeringMapper = mock(CourseOfferingMapper.class);
    AssessmentExecutionService executionService = mock(AssessmentExecutionService.class);
    ExamReviewerQueryMapper reviewerQueryMapper = mock(ExamReviewerQueryMapper.class);
    CourseOffering offering = new CourseOffering();
    offering.setCourseOfferingId(10L);
    offering.setCourseVersionId(20L);
    when(offeringMapper.selectById(10L)).thenReturn(offering);
    AssessmentExecution execution = new AssessmentExecution();
    execution.setAssessmentExecutionId(30L);
    execution.setSourceType("COURSE_QUIZ");
    when(executionService.findCourse(10L, 20L, 40L)).thenReturn(execution);
    ExamReviewerVO reviewer = new ExamReviewerVO();
    reviewer.setAssessmentExecutionId(30L);
    when(reviewerQueryMapper.selectReviewers(30L)).thenReturn(List.of(reviewer));
    CourseAssessmentManagementService service = new CourseAssessmentManagementService(
      offeringMapper, mock(CourseMapper.class), mock(CourseVersionMapper.class), mock(CourseSnapshotAssembler.class),
      executionService, mock(ExamReviewerMapper.class), reviewerQueryMapper,
      mock(ExamAttemptQueryMapper.class), mock(CourseAssessmentResultMapper.class),
      mock(SysUserMapper.class), mock(ExamAttemptService.class));

    var result = service.listReviewers(10L, 40L);

    assertThat(result).singleElement()
      .extracting(ExamReviewerVO::getAssessmentExecutionId).isEqualTo(30L);
  }
}

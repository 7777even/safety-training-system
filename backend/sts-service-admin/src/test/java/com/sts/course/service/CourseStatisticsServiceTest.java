package com.sts.course.service;
import com.sts.course.mapper.*;
import com.sts.exam.mapper.*;
import com.sts.admin.mapper.SysUserMapper;
import com.sts.question.mapper.KnowledgePointMapper;
import com.sts.course.entity.CourseOffering;
import com.sts.exam.entity.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.assertj.core.api.Assertions.assertThat;
/** 没有人报名/交卷也必须展示已冻结题池覆盖，不能把覆盖当成绩。 */
class CourseStatisticsServiceTest {
  @Test void historyKeepsWithdrawnWhileSummaryExcludesIt() {
    com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(new org.apache.ibatis.builder.MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(),"batch5"),com.sts.course.entity.CourseEnrollment.class);
    var offerings=mock(CourseOfferingMapper.class);var enrollments=mock(CourseEnrollmentMapper.class);var o=new CourseOffering();o.setCourseOfferingId(1L);o.setCourseId(2L);o.setOfferingName("开班");when(offerings.selectList(any())).thenReturn(List.of(o));
    var record=new com.sts.course.entity.CourseEnrollment();record.setCourseEnrollmentId(3L);record.setUserId(7L);record.setLearningStatus("WITHDRAWN");record.setCourseOfferingId(1L);record.setTotalStudySeconds(60);
    when(enrollments.selectList(any())).thenAnswer(inv->{com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.sts.course.entity.CourseEnrollment> query=inv.getArgument(0);return query.getSqlSegment().contains("<>")?List.of():List.of(record);});
    var service=new CourseStatisticsService(mock(CourseMapper.class),offerings,enrollments,mock(CourseStudySessionMapper.class),mock(CourseCourseCategoryMapper.class),mock(CourseCategoryMapper.class),mock(ExamCandidateMapper.class),mock(ExamAttemptMapper.class),mock(ExamAttemptQuestionMapper.class),mock(ExamAnswerMapper.class),mock(AssessmentExecutionMapper.class),mock(AssessmentExecutionQuestionPoolMapper.class),mock(KnowledgePointMapper.class),mock(SysUserMapper.class),new ObjectMapper());
    assertThat(service.people(null,null,null,1,20).list()).singleElement().satisfies(p->{assertThat(p.learningStatus()).isEqualTo("WITHDRAWN");assertThat(p.totalStudySeconds()).isEqualTo(60);});
    assertThat(service.statistics(null,null).summary().enrolledCount()).isZero();
  }
  @Test void frozenKnowledgeCoverageExistsBeforeAnySubmission() {
    var offerings=mock(CourseOfferingMapper.class);var executions=mock(AssessmentExecutionMapper.class);var pools=mock(AssessmentExecutionQuestionPoolMapper.class);
    var offering=new CourseOffering();offering.setCourseOfferingId(1L);offering.setCourseId(2L);offering.setOfferingName("开班");
    when(offerings.selectList(any())).thenReturn(List.of(offering));
    var execution=new AssessmentExecution();execution.setAssessmentExecutionId(3L);execution.setPolicySnapshotJson("{\"knowledgePointSnapshots\":[{\"questionId\":5,\"questionVersionId\":4,\"knowledgePointIds\":[7]}]}");execution.setOrganizationId(1L);execution.setSourceType("COURSE_QUIZ");execution.setSourceBusinessId(5L);execution.setAssessmentKind("QUIZ");execution.setResourceType("PAPER_VERSION");execution.setResourceId(31L);execution.setResourceNameSnapshot("试卷");execution.setDurationSecondsSnapshot(60);new com.sts.exam.service.AssessmentExecutionSnapshotCodec(new ObjectMapper()).encode(execution);execution.setPolicySnapshotJson(null);
    when(executions.selectList(any())).thenReturn(List.of(execution));
    var q=new AssessmentExecutionQuestionPool();q.setQuestionVersionId(4L);q.setKnowledgePointIdsJson("[7]");
    when(pools.selectList(any())).thenReturn(List.of());
    var service=new CourseStatisticsService(mock(CourseMapper.class),offerings,mock(CourseEnrollmentMapper.class),mock(CourseStudySessionMapper.class),
      mock(CourseCourseCategoryMapper.class),mock(CourseCategoryMapper.class),mock(ExamCandidateMapper.class),mock(ExamAttemptMapper.class),mock(ExamAttemptQuestionMapper.class),
      mock(ExamAnswerMapper.class),executions,pools,mock(KnowledgePointMapper.class),mock(SysUserMapper.class),new ObjectMapper());
    var result=service.statistics(null,null);
    assertThat(result.knowledgePoints()).singleElement().satisfies(k->{assertThat(k.questionCount()).isEqualTo(1);assertThat(k.answeredCount()).isZero();assertThat(k.accuracyRate()).isNull();});
  }
}

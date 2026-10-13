package com.sts.exam.mapper;
import com.sts.admin.support.AbstractMysqlIntegrationTest;
import com.sts.admin.tenant.TenantContext;
import com.sts.exam.support.ExamMapperItConfig;
import com.sts.exam.entity.ExamAttemptQuestion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.assertThat;
/** 新快照真实JSON持久化、历史空值及租户边界；不接触开发库。 */
@SpringBootTest(classes=ExamMapperItConfig.class,webEnvironment=SpringBootTest.WebEnvironment.NONE)
@Transactional
class Batch5StatisticsFactsIT extends AbstractMysqlIntegrationTest {
  @Autowired ExamAttemptQuestionMapper questions;
  @Autowired AssessmentExecutionMapper executions;
  @Autowired com.sts.course.mapper.CourseEnrollmentMapper enrollments;
  /** 模拟真实 Mapper 往返，不能依赖不落库的 policy 视图字段。 */
  @Test void persistedCourseKnowledgeIsHydratedAndVerified() {
    var mapper=new com.fasterxml.jackson.databind.ObjectMapper();var execution=new com.sts.exam.entity.AssessmentExecution();
    execution.setOrganizationId(demoTenantId());execution.setSourceType("COURSE_QUIZ");execution.setSourceBusinessId(990101L);execution.setCourseOfferingId(990102L);execution.setCourseVersionId(990103L);execution.setCourseItemId(990101L);execution.setAssessmentKind("QUIZ");execution.setResourceType("PAPER_VERSION");execution.setResourceId(990104L);execution.setResourceNameSnapshot("冻结试卷");execution.setDurationSecondsSnapshot(60);execution.setExecutionStatus("READY");
    execution.setPolicySnapshotJson("{\"knowledgePointSnapshots\":[{\"questionId\":5,\"questionVersionId\":9,\"knowledgePointIds\":[7]}]}");new com.sts.exam.service.AssessmentExecutionSnapshotCodec(mapper).encode(execution);executions.insert(execution);
    var reloaded=executions.selectById(execution.getAssessmentExecutionId());assertThat(reloaded.getPolicySnapshotJson()).isNull();
    assertThat(com.sts.exam.service.CourseKnowledgeSnapshotService.read(reloaded,mapper)).singleElement().satisfies(q->assertThat(q.knowledgePointIds()).containsExactly(7L));
    assertThat(TenantContext.callWithTenant(demoTenantId()+10000,()->executions.selectById(execution.getAssessmentExecutionId()))).isNull();
  }
  /** 真实 SQL 保留退课历史，统计口径单独限定有效报名。 */
  @Test void withdrawnHistoryAndTenantFilterUseRealSql() {
    var row=new com.sts.course.entity.CourseEnrollment();row.setCourseOfferingId(990201L);row.setUserId(990202L);row.setEnrollmentSource("SELF");row.setEnrolledTime(java.time.LocalDateTime.now());row.setLearningStatus("WITHDRAWN");row.setProgressPercent(java.math.BigDecimal.TEN);row.setTotalStudySeconds(60);row.setCreditAwarded(java.math.BigDecimal.ZERO);enrollments.insert(row);
    assertThat(enrollments.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.sts.course.entity.CourseEnrollment>().eq(com.sts.course.entity.CourseEnrollment::getCourseOfferingId,990201L))).hasSize(1);
    assertThat(enrollments.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.sts.course.entity.CourseEnrollment>().eq(com.sts.course.entity.CourseEnrollment::getCourseOfferingId,990201L).ne(com.sts.course.entity.CourseEnrollment::getLearningStatus,"WITHDRAWN"))).isEmpty();
    assertThat(TenantContext.callWithTenant(demoTenantId()+10000,()->enrollments.selectById(row.getCourseEnrollmentId()))).isNull();
  }

  @Test void knowledgeSnapshotPersistsAndCannotBeReadAcrossTenants() {
    var q=new ExamAttemptQuestion();q.setExamAttemptId(990001L);q.setQuestionId(990002L);q.setQuestionVersionId(990003L);
    q.setQuestionType("SINGLE_CHOICE");q.setStemSnapshot("快照题目");q.setGradingMode("AUTO");q.setScore(java.math.BigDecimal.ONE);q.setSortOrder(1);q.setSourceBankId(990004L);
    q.setKnowledgePointIdsSnapshotJson("[7,8]");questions.insert(q);
    assertThat(questions.selectById(q.getAttemptQuestionId()).getKnowledgePointIdsSnapshotJson()).contains("7","8");
    assertThat(TenantContext.callWithTenant(demoTenantId()+10000,()->questions.selectById(q.getAttemptQuestionId()))).isNull();
  }
  @Test void historicalQuestionRemainsUnknownRatherThanEmptyAssociation() {
    var q=new ExamAttemptQuestion();q.setExamAttemptId(990011L);q.setQuestionId(990012L);q.setQuestionVersionId(990013L);
    q.setQuestionType("SINGLE_CHOICE");q.setStemSnapshot("历史题目");q.setGradingMode("AUTO");q.setScore(java.math.BigDecimal.ONE);q.setSortOrder(1);q.setSourceBankId(990014L);
    questions.insert(q);assertThat(questions.selectById(q.getAttemptQuestionId()).getKnowledgePointIdsSnapshotJson()).isNull();
  }
}

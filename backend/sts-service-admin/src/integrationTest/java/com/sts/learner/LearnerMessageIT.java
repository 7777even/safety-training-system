package com.sts.learner;

import cn.dev33.satoken.stp.StpUtil;
import com.sts.admin.support.AbstractMysqlIntegrationTest;
import com.sts.admin.support.MapperItConfig;
import com.sts.admin.tenant.TenantContext;
import com.sts.common.api.PageRequest;
import com.sts.learner.mapper.*;
import com.sts.learner.service.LearnerMessageService;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import javax.sql.DataSource;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mockStatic;

/** 真MySQL验证公开消息行为的幂等、事务与租户，不使用内存库替代。 */
@SpringBootTest(classes = LearnerMessageIT.Config.class)
class LearnerMessageIT extends AbstractMysqlIntegrationTest {
  @Configuration @Import(MapperItConfig.class) @MapperScan("com.sts.learner.mapper")
  static class Config { }
  @Autowired LearnerMessageMapper mapper;
  @Autowired LearnerNotificationSourceMapper sources;
  @Autowired DataSource dataSource;
  /** JDBC夹具改动后清理MyBatis一级查询缓存，读取真实数据库状态。 */
  @Autowired SqlSessionTemplate sqlSession;

  @Test void committedMessageIsIdempotentAndRollbackDoesNotDeliver() {
    var service = new LearnerMessageService(mapper);
    var tx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    Long org = demoTenantId();
    String event = UUID.randomUUID().toString();
    try (var stp = mockStatic(StpUtil.class)) {
      stp.when(StpUtil::getLoginIdAsLong).thenReturn(98765001L);
      tx.executeWithoutResult(status -> service.send(event, "COURSE", "已分配课程", "安排通知", 31L, null, null, List.of(98765001L)));
      tx.executeWithoutResult(status -> service.send(event, "COURSE", "禁止覆盖标题", "重复通知", 31L, null, null, List.of(98765001L)));
      var result = service.page(new PageRequest<>(1, 20, null));
      assertThat(result.total()).isEqualTo(1);
      assertThat(result.list().getFirst().title()).isEqualTo("已分配课程");
      assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
        service.send(event + "-rollback", "COURSE", "回滚消息", "不得投递", 32L, null, null, List.of(98765001L));
        throw new IllegalStateException("模拟业务回滚");
      })).isInstanceOf(IllegalStateException.class);
      assertThat(service.page(new PageRequest<>(1, 20, null)).total()).isEqualTo(1);
      assertThat(TenantContext.callWithTenant(org + 98765, () -> service.page(new PageRequest<>(1, 20, null)).total())).isZero();
      stp.when(StpUtil::getLoginIdAsLong).thenReturn(98765002L);
      assertThat(service.page(new PageRequest<>(1, 20, null)).total()).isZero();
      assertThat(mapper.counts(org, List.of(98765001L)).getFirst().total()).isEqualTo(1);
    }
  }

  @Test void notificationSourcesUseRealSchemaAndDoNotInventMissingAssignments() {
    Long org = demoTenantId(), absent = Long.MAX_VALUE;
    assertThat(sources.courses(org, absent)).isEmpty();
    assertThat(sources.exams(org, absent)).isEmpty();
    assertThat(sources.quizzes(org, absent)).isEmpty();
    assertThat(sources.trainings(org, absent)).isEmpty();
    assertThat(sources.courseAudience(org, absent)).isEmpty();
    assertThat(sources.tenants()).contains(org);
    assertThat(sources.courseState(org, absent)).isNull();
  }

  /** 正向分配及排除行覆盖真实JOIN、发布状态、机构范围和报名/关注去重。 */
  @Test @Transactional void sourcesKeepAssignedRecipientsAndExcludeOtherTenantsAndTrainingExams() {
    var jdbc = new JdbcTemplate(dataSource);
    Long org = demoTenantId(); long other = org + 98765, course = 98766001, offering = 98766002;
    jdbc.update("""
      INSERT INTO sts_course_offering(course_offering_id,organization_id,offering_code,course_id,offering_name,
        course_type,enrollment_type,eligibility_type,completion_rule,publish_status,status)
      VALUES(?,?, 'BATCH4-IT',?,'测试开课','REQUIRED','ASSIGNED','ALL','ALL_ITEMS','PUBLISHED',1)
      """, offering, org, course);
    for (long user : List.of(98766011L, 98766012L, 98766013L)) jdbc.update("""
      INSERT INTO sts_course_enrollment(organization_id,course_offering_id,user_id,enrollment_source,enrolled_time,
        learning_status,progress_percent,total_study_seconds,credit_awarded)
      VALUES(?,?,?, ?,NOW(),'NOT_STARTED',0,0,0)
      """, user == 98766013 ? other : org, offering, user, user == 98766012 ? "SELF" : "ASSIGN");
    jdbc.update("""
      INSERT INTO sts_course_favorite(organization_id,user_id,course_id,is_favorite,course_name_snapshot)
      VALUES(?,98766011,?,1,'测试课程'),(?,98766014,?,1,'测试课程'),(?,98766015,?,1,'跨机构课程')
      """, org, course, org, course, other, course);
    assertThat(sources.courses(org, offering)).extracting(LearnerNotificationSourceMapper.Target::userId).containsExactly(98766011L);
    assertThat(sources.courseAudience(org, course)).containsExactlyInAnyOrder(98766011L, 98766012L, 98766014L);
    jdbc.update("UPDATE sts_course_offering SET publish_status='CANCELLED' WHERE course_offering_id=?", offering);
    sqlSession.clearCache();
    assertThat(sources.courses(org, offering)).isEmpty();
    jdbc.update("UPDATE sts_course_offering SET publish_status='PUBLISHED' WHERE course_offering_id=?", offering);
    sqlSession.clearCache();

    for (int index = 0; index < 3; index++) {
      long exam = 98766100 + index, execution = 98766200 + index;
      jdbc.update("""
        INSERT INTO sts_exam_exam(exam_id,organization_id,exam_code,exam_name,exam_type,duration_seconds,
          pass_ratio,pass_score,credit,question_order_mode,multi_select_scoring,is_notice_enabled,publish_status,status)
        VALUES(?,?,?,'安排考试','SPECIAL_PRACTICE',3600,0.6,60,0,'TYPE','ALL_OR_NOTHING',0,'PUBLISHED',1)
        """, exam, org, "BATCH4-" + index);
      jdbc.update("""
        INSERT INTO sts_assessment_execution(assessment_execution_id,organization_id,source_type,source_business_id,
          exam_id,assessment_kind,resource_type,resource_id,snapshot_schema_version,snapshot_json,snapshot_hash,execution_status)
        VALUES(?,?,?,?,?,'SPECIAL_PRACTICE','PAPER_VERSION',?,1,'{}',REPEAT('a',64),'READY')
        """, execution, org, index == 1 ? "TRAINING_PRACTICE" : "STANDALONE_PRACTICE", exam, exam, exam);
      jdbc.update("""
        INSERT INTO sts_exam_candidate(organization_id,assessment_execution_id,user_id,assignment_source,
          candidate_profile_snapshot_json,profile_snapshot_time,candidate_status,attempt_count)
        VALUES(?,?,?,'ASSIGN','{}',NOW(),'NOT_STARTED',0)
        """, index == 2 ? other : org, execution, 98766011L + index);
    }
    assertThat(sources.exams(org, 98766100L)).extracting(LearnerNotificationSourceMapper.Target::userId).containsExactly(98766011L);
    assertThat(sources.exams(org, 98766101L)).isEmpty();
    assertThat(sources.exams(org, 98766102L)).isEmpty();
    jdbc.update("""
      INSERT INTO sts_assessment_execution(organization_id,source_type,source_business_id,course_offering_id,course_version_id,
        course_item_id,assessment_kind,resource_type,resource_id,snapshot_schema_version,snapshot_json,snapshot_hash,execution_status)
      VALUES(?,'COURSE_QUIZ',?, ?,1,98766300,'QUIZ','PAPER_VERSION',1,1,'{}',REPEAT('a',64),'READY')
      """, org, offering, offering);
    assertThat(sources.quizzes(org, offering)).extracting(LearnerNotificationSourceMapper.Target::userId).containsExactly(98766011L);
    jdbc.update("""
      INSERT INTO sts_training_plan(training_plan_id,organization_id,plan_code,plan_name,training_type_id,training_nature,
        start_time,end_time,publish_status,status) VALUES(98766400,?,'BATCH4-IT','安排培训',1,'INITIAL',NOW(),DATE_ADD(NOW(),INTERVAL 2 DAY),'RUNNING',1)
      """, org);
    jdbc.update("""
      INSERT INTO sts_training_participant(organization_id,training_plan_id,user_id,participant_org_id,join_source,
        joined_time,participant_status,online_hours,offline_hours) VALUES(?,98766400,98766011,?,'ASSIGN',NOW(),'PENDING',0,0)
      """, org, org);
    assertThat(sources.trainings(org, 98766400L)).extracting(LearnerNotificationSourceMapper.Target::userId).containsExactly(98766011L);
    assertThat(TenantContext.callWithTenant(other, () -> sources.trainings(other, 98766400L))).isEmpty();
  }
}

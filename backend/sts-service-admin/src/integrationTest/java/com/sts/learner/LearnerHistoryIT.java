package com.sts.learner;

import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sts.admin.support.*;
import com.sts.admin.tenant.TenantContext;
import com.sts.archive.dto.HistoryCorrectionRequest;
import com.sts.common.api.PageRequest;
import com.sts.learner.mapper.LearnerHistoryMapper;
import com.sts.learner.service.LearnerHistoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.mybatis.spring.annotation.MapperScan;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 真MySQL验证所有来源、退出记录、成绩裁剪、版本追加/回滚及冻结资产不变。 */
@SpringBootTest(classes = LearnerHistoryIT.Config.class,properties="mybatis-plus.configuration.log-impl=org.apache.ibatis.logging.nologging.NoLoggingImpl")
class LearnerHistoryIT extends AbstractMysqlIntegrationTest {
  @Configuration @Import(MapperItConfig.class) @MapperScan("com.sts.learner.mapper") @EnableTransactionManagement
  static class Config {
    @Bean ObjectMapper json() { return new ObjectMapper().findAndRegisterModules(); }
    @Bean LearnerHistoryService history(LearnerHistoryMapper mapper,ObjectMapper json) { return new LearnerHistoryService(mapper,json); }
    @Bean TransactionTemplate transaction(org.springframework.transaction.PlatformTransactionManager manager) { return new TransactionTemplate(manager); }
  }
  @Autowired LearnerHistoryService history;
  @Autowired JdbcTemplate jdbc;
  @Autowired ObjectMapper json;
  @Autowired TransactionTemplate transaction;

  @Test void sourcesCorrectionsAndFrozenFactsRemainTraceable() {
    Long org=demoTenantId(); long user=98769001L, course=98769101L, offering=98769201L, exam=98769301L, plan=98769401L;
    jdbc.update("INSERT INTO us_sys_user(user_id,organization_id,user_code,user_name,member_type,status) VALUES(?,?,'B4-HISTORY','当前姓名','INTERNAL',1)",user,org);
    jdbc.update("""
      INSERT INTO sts_course_offering(course_offering_id,organization_id,offering_code,course_id,offering_name,course_type,
        enrollment_type,eligibility_type,completion_rule,publish_status,status)
      VALUES(?,?, 'B4-HISTORY',?,'已退出课程','REQUIRED','ASSIGNED','ALL','ALL_ITEMS','ENDED',1)
      """,offering,org,course);
    jdbc.update("""
      INSERT INTO sts_course_enrollment(course_enrollment_id,organization_id,course_offering_id,user_id,enrollment_source,
        enrolled_time,learning_status,progress_percent,total_study_seconds,credit_awarded,deleted,result_snapshot_json)
      VALUES(98769501,?,?,?,'ASSIGN',NOW(),'COMPLETED',100,7200,0,98769501,'{"original":true}')
      """,org,offering,user);
    jdbc.update("""
      INSERT INTO sts_exam_exam(exam_id,organization_id,exam_code,exam_name,exam_type,duration_seconds,pass_ratio,pass_score,
        credit,question_order_mode,multi_select_scoring,is_notice_enabled,publish_status,status)
      VALUES(?,?,'B4-HISTORY','已结束考试','ONLINE',3600,0.6,60,0,'TYPE','ALL_OR_NOTHING',0,'ENDED',1)
      """,exam,org);
    for(int index=0;index<2;index++) {
      long execution=98769601+index,candidate=98769701+index;
      jdbc.update("""
        INSERT INTO sts_assessment_execution(assessment_execution_id,organization_id,source_type,source_business_id,exam_id,
          assessment_kind,resource_type,resource_id,snapshot_schema_version,snapshot_json,snapshot_hash,execution_status)
        VALUES(?,?,?,?,?,'QUIZ','PAPER_VERSION',1,1,'{"resource":{"name":"随堂测试"},"rules":{"totalScore":"100"}}',REPEAT('a',64),'ENDED')
        """,execution,org,index==0?"STANDALONE_EXAM":"COURSE_QUIZ",exam,index==0?exam:null);
      jdbc.update("""
        INSERT INTO sts_exam_candidate(exam_candidate_id,organization_id,assessment_execution_id,user_id,course_enrollment_id,
          course_item_id,assignment_source,candidate_profile_snapshot_json,profile_snapshot_time,candidate_status,attempt_count,final_score)
        VALUES(?,?,?,?,?,?,'ASSIGN','{"userName":"冻结姓名","userCode":"B4-HISTORY"}',NOW(),'FAILED',0,55)
        """,candidate,org,execution,user,index==0?0:98769501,index==0?0:98769901);
    }
    for(String mode:List.of("PRACTICE","SIMULATION")) jdbc.update("""
      INSERT INTO sts_learner_practice_session(organization_id,user_id,mode,title,status,client_token,started_time,submitted_time)
      VALUES(?,?,?,'历史个人练习','SUBMITTED',?,NOW(),NOW())
      """,org,user,mode,"B4-HISTORY-"+mode);
    jdbc.update("""
      INSERT INTO sts_training_plan(training_plan_id,organization_id,plan_code,plan_name,training_type_id,training_nature,
        start_time,end_time,publish_status,status) VALUES(?,?,'B4-HISTORY','已结束培训',1,'INITIAL',NOW(),NOW(),'ENDED',1)
      """,plan,org);
    jdbc.update("""
      INSERT INTO sts_training_participant(training_participant_id,organization_id,training_plan_id,user_id,participant_org_id,
        join_source,joined_time,participant_status,online_hours,offline_hours,composite_score,result_finalized_time,
        final_result_snapshot_json,final_result_snapshot_hash)
      VALUES(98769801,?,?,?,?,'ASSIGN',NOW(),'WITHDRAWN',2,1,80,NOW(),'{"frozen":true}',REPEAT('b',64))
      """,org,plan,user,org);
    String frozen=jdbc.queryForObject("SELECT final_result_snapshot_json FROM sts_training_participant WHERE training_participant_id=98769801",String.class);
    String examSnapshot=jdbc.queryForObject("SELECT candidate_profile_snapshot_json FROM sts_exam_candidate WHERE exam_candidate_id=98769701",String.class);
    try(var auth=mockStatic(StpUtil.class)) {
      auth.when(StpUtil::getLoginIdAsLong).thenReturn(user);
      auth.when(() -> StpUtil.hasPermission(anyString())).thenReturn(true);
      var page=history.mine(new PageRequest<>(1,20,null));
      assertThat(page.list()).extracting(r->r.recordType()).containsExactlyInAnyOrder("COURSE","FORMAL_EXAM","COURSE_QUIZ","PRACTICE","SIMULATION","TRAINING");
      var formal=history.myDetail("FORMAL_EXAM",98769701L); assertThat(formal.reference().resourceId()).isEqualTo(98769701L);
      assertThat(formal.original().score()).isNull(); assertThat(formal.original().userName()).isEqualTo("冻结姓名");
      assertThat(history.myDetail("COURSE",98769501L).original().hours()).isEqualByComparingTo("2");
      assertThat(history.myDetail("COURSE_QUIZ",98769702L).reference().resourceId()).isEqualTo(98769501L);
      var request=new HistoryCorrectionRequest(); request.setVersion(0); request.setReason("核实历史成绩"); request.setChanges(Map.of("score",json.valueToTree(60),"userName",json.valueToTree("更正姓名")));
      assertThat(history.correct(user,"FORMAL_EXAM",98769701L,request).version()).isEqualTo(1);
      assertThat(history.myDetail("FORMAL_EXAM",98769701L).corrections()).allSatisfy(c->{ assertThat(c.before()).doesNotContainKey("score"); assertThat(c.after()).doesNotContainKey("score"); });
      assertThatThrownBy(()->history.correct(user,"FORMAL_EXAM",98769701L,request)).hasMessageContaining("最新版本");
      request.setVersion(1); request.setChanges(Map.of("userName",json.valueToTree("第二次更正")));
      assertThatThrownBy(()->transaction.executeWithoutResult(s->{ history.correct(user,"FORMAL_EXAM",98769701L,request); throw new IllegalStateException("回滚"); })).hasMessageContaining("回滚");
      assertThat(history.myDetail("FORMAL_EXAM",98769701L).version()).isEqualTo(1);
      history.correct(user,"FORMAL_EXAM",98769701L,request);
      assertThat(history.myDetail("FORMAL_EXAM",98769701L).corrections().getLast().before().get("userName").asText()).isEqualTo("更正姓名");
      assertThat(jdbc.queryForObject("SELECT final_score FROM sts_exam_candidate WHERE exam_candidate_id=98769701",java.math.BigDecimal.class)).isEqualByComparingTo("55");
      assertThat(jdbc.queryForObject("SELECT candidate_profile_snapshot_json FROM sts_exam_candidate WHERE exam_candidate_id=98769701",String.class)).isEqualTo(examSnapshot);
      assertThat(jdbc.queryForObject("SELECT final_result_snapshot_json FROM sts_training_participant WHERE training_participant_id=98769801",String.class)).isEqualTo(frozen);
      assertThat(TenantContext.callWithTenant(org+1000,()-> { try { history.corrections(user,"FORMAL_EXAM",98769701L); return false; } catch(com.sts.common.exception.BusinessException e) {return true;} })).isTrue();
    }
    // 两个授权请求使用同一旧展示版本，真行锁确保只有一个提交。
    var workers=java.util.concurrent.Executors.newFixedThreadPool(2);
    var start=new java.util.concurrent.CountDownLatch(1);
    try {
      var jobs=new ArrayList<java.util.concurrent.Future<Boolean>>();
      for(int i=0;i<2;i++) { final int index=i; jobs.add(workers.submit(() -> {
        start.await();
        try(var auth=mockStatic(StpUtil.class)) {
          auth.when(StpUtil::getLoginIdAsLong).thenReturn(user); auth.when(() -> StpUtil.hasPermission(anyString())).thenReturn(true);
          return TenantContext.callWithTenant(org,()-> {
            var r=new HistoryCorrectionRequest(); r.setVersion(0); r.setReason("并发核实"); r.setChanges(Map.of("userName",json.valueToTree("并发更正"+index)));
            try { history.correct(user,"TRAINING",98769801L,r); return true; }
            catch(com.sts.common.exception.BusinessException ex) { assertThat(ex.getCode()).isEqualTo(2040); return false; }
          });
        }
      })); }
      start.countDown(); int success=0; for(var result:jobs) if(result.get(30,java.util.concurrent.TimeUnit.SECONDS)) success++;
      assertThat(success).isEqualTo(1);
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sts_archive_history_correction WHERE user_id=? AND record_type='TRAINING'",Integer.class,user)).isEqualTo(1);
      assertThat(jdbc.queryForObject("SELECT final_result_snapshot_json FROM sts_training_participant WHERE training_participant_id=98769801",String.class)).isEqualTo(frozen);
    } catch(Exception e) { throw new AssertionError("并发更正未完成",e); }
    finally { workers.shutdownNow(); }
  }
}

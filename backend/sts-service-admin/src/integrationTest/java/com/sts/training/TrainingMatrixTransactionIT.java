package com.sts.training;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sts.admin.support.*;
import com.sts.admin.tenant.TenantContext;
import com.sts.training.dto.*;
import com.sts.training.service.*;
import com.sts.training.vo.TrainingPlanVO;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
/** 用真实事务验证编排失败不遗留计划；仅下游业务服务用桩制造失败。 */
@SpringBootTest(classes=TrainingMatrixTransactionIT.Config.class)
class TrainingMatrixTransactionIT extends AbstractMysqlIntegrationTest {
  @org.springframework.transaction.annotation.EnableTransactionManagement
  @Configuration @Import({MapperItConfig.class,TrainingMatrixService.class})
  @MapperScan({"com.sts.training.mapper","com.sts.org.mapper","com.sts.course.mapper"})
  static class Config {
    @Bean ObjectMapper json() { return new ObjectMapper().findAndRegisterModules(); }
    @Bean TrainingPlanService plans() { return mock(TrainingPlanService.class); }
  }
  @Autowired TrainingMatrixService matrices;
  @Autowired TrainingPlanService plans;
  @Autowired JdbcTemplate jdbc;
  @Test void compositionFailureRollsBackCreatedPlan() {
    Long org=TenantContext.requireTenantId();
    jdbc.update("INSERT INTO sts_org_position(organization_id,position_code,position_name,description,status) VALUES (?,'b7tx','事务岗位','',1)",org);
    Long position=jdbc.queryForObject("SELECT position_id FROM sts_org_position WHERE organization_id=? AND position_code='b7tx'",Long.class,org);
    jdbc.update("INSERT INTO sts_course_course(organization_id,course_code,course_name,content_mode,credit,point_mode,point_value,source_download_mode,is_speed_play_allowed,is_study_anticheat_enabled,is_study_description_enabled,is_watermark_enabled,publish_status,status) VALUES (?,'b7tx','事务课程','SINGLE',0,'NONE',0,'NONE',0,0,0,0,'PUBLISHED',1)",org);
    Long course=jdbc.queryForObject("SELECT course_id FROM sts_course_course WHERE organization_id=? AND course_code='b7tx'",Long.class,org);
    jdbc.update("INSERT INTO sts_course_version(organization_id,course_id,version_no,content_snapshot_json,snapshot_hash,published_by,published_time) VALUES (?,?,1,'{}','b7tx',1,NOW())",org,course);
    Long version=jdbc.queryForObject("SELECT course_version_id FROM sts_course_version WHERE organization_id=? AND course_id=?",Long.class,org,course);
    var matrix = new TrainingMatrixRequest(); matrix.setPositionId(position); matrix.setContentName("事务内容");matrix.setCourseId(course);matrix.setCourseVersionId(version);matrix.setRequiredHours(new java.math.BigDecimal("2"));matrix.setStatus(1);
    Long id=matrices.save(null,matrix);
    when(plans.create(any())).thenAnswer(call -> {
      jdbc.update("INSERT INTO sts_training_plan(organization_id,plan_code,plan_name,training_type_id,training_nature,start_time,end_time,publish_status,status) VALUES (?,'b7txrollback','回滚计划',1,'INITIAL',NOW(),DATE_ADD(NOW(),INTERVAL 1 DAY),'DRAFT',1)",org);
      return jdbc.queryForObject("SELECT training_plan_id FROM sts_training_plan WHERE organization_id=? AND plan_code='b7txrollback'",Long.class,org);
    });
    var vo=new TrainingPlanVO();vo.setVersion(0);when(plans.get(any())).thenReturn(vo);
    doThrow(new IllegalStateException("模拟课表保存失败")).when(plans).replaceStages(any(),any());
    var request=new TrainingMatrixPlanRequest();request.setPlan(new TrainingPlanCreateRequest());request.setMatrixIds(List.of(id));
    assertThatThrownBy(() -> matrices.createPlan(request)).isInstanceOf(IllegalStateException.class);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sts_training_plan WHERE organization_id=? AND plan_code='b7txrollback'",Long.class,org)).isZero();
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sts_training_matrix WHERE organization_id=? AND matrix_id=?",Long.class,org,id)).isEqualTo(1);
  }
}

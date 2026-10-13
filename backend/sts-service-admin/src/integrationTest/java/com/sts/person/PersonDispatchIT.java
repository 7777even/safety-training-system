package com.sts.person;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sts.admin.support.*;
import com.sts.admin.tenant.TenantContext;
import com.sts.admin.system.service.SysDeptService;
import com.sts.person.dto.*;
import com.sts.person.service.*;
import com.sts.training.mapper.TrainingParticipantMapper;
import com.sts.training.entity.TrainingParticipant;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
/** 真库验证用户组动态展开与已下发具体参训关系分离。 */
@SpringBootTest(classes=PersonDispatchIT.Config.class) @Transactional
class PersonDispatchIT extends AbstractMysqlIntegrationTest {
  @Configuration @Import({MapperItConfig.class,PersonClassificationService.class,PersonSelectionServiceImpl.class})
  @MapperScan({"com.sts.person.mapper","com.sts.org.mapper","com.sts.training.mapper"})
  static class Config {
    @Bean ObjectMapper objectMapper() { return new ObjectMapper().findAndRegisterModules(); }
    @Bean SysDeptService deptService() { return org.mockito.Mockito.mock(SysDeptService.class); }
  }
  @Autowired PersonSelectionService selection;
  @Autowired JdbcTemplate jdbc;
  /** JDBC旁路写入后清理查询缓存，确保读取当前数据库事实。 */
  @Autowired org.mybatis.spring.SqlSessionTemplate sqlSession;
  @Autowired TrainingParticipantMapper participants;
  @Test void currentMembersChangeWhileAssignedSnapshotRemains() {
    Long org = TenantContext.requireTenantId();
    jdbc.update("INSERT INTO us_sys_user(organization_id,user_code,user_name,status) VALUES (?,'b7dispatch','组下发学员',1)",org);
    Long user = jdbc.queryForObject("SELECT user_id FROM us_sys_user WHERE user_code='b7dispatch' AND organization_id=?",Long.class,org);
    jdbc.update("INSERT INTO sts_person_profile(organization_id,user_id,occupation) VALUES (?,?,'电工')",org,user);
    jdbc.update("INSERT INTO sts_person_group(organization_id,group_name,status,conditions_json) VALUES (?,'b7dispatch',1,'{\"occupation\":\"电工\"}')",org);
    Long group = jdbc.queryForObject("SELECT group_id FROM sts_person_group WHERE group_name='b7dispatch' AND organization_id=?",Long.class,org);
    var request = new PersonSelectionRequest(); request.setSelectionType("GROUP"); request.setSubjectIds(List.of(group));
    assertThat(selection.resolve(request)).contains(user);
    jdbc.update("INSERT INTO sts_training_participant(organization_id,training_plan_id,user_id,participant_org_id,join_source,joined_time,participant_status,online_hours,offline_hours) VALUES (?,99,?,?,'ASSIGN',NOW(),'PENDING',0,0)",org,user,org);
    jdbc.update("UPDATE sts_person_profile SET occupation='焊工' WHERE organization_id=? AND user_id=?",org,user);
    sqlSession.clearCache();
    assertThatThrownBy(() -> selection.resolve(request)).hasMessageContaining("没有符合条件");
    assertThat(participants.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<TrainingParticipant>().eq(TrainingParticipant::getTrainingPlanId,99L)))
      .extracting(TrainingParticipant::getUserId).contains(user);
    jdbc.update("UPDATE sts_person_group SET status=0 WHERE group_id=?",group);
    sqlSession.clearCache();
    assertThatThrownBy(() -> selection.resolve(request)).hasMessageContaining("已停用");
    Long tenant=TenantContext.requireTenantId();
    try { TenantContext.setTenantId(tenant+10000); assertThatThrownBy(() -> selection.resolve(request)).hasMessageContaining("不属于当前机构"); }
    finally { TenantContext.setTenantId(tenant); }
  }
}

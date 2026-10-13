package com.sts.person;

import com.sts.admin.entity.SysUser;
import com.sts.admin.mapper.SysUserMapper;
import com.sts.admin.support.*;
import com.sts.admin.tenant.TenantContext;
import com.sts.person.dto.PersonClassificationCondition;
import com.sts.person.entity.PersonProfile;
import com.sts.person.mapper.*;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.LocalDate;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

/** 真实MySQL验证分类交并集、年龄边界、空特征、清空与租户插件。 */
@SpringBootTest(classes=PersonClassificationIT.Config.class)
@Transactional
class PersonClassificationIT extends AbstractMysqlIntegrationTest {
  @Configuration @Import(MapperItConfig.class) @MapperScan("com.sts.person.mapper")
  static class Config { }
  @Autowired SysUserMapper users;
  @Autowired PersonProfileMapper profiles;
  @Autowired PersonClassificationMapper people;
  @Autowired JdbcTemplate jdbc;
  /** JDBC旁路写入后清理查询缓存，确保读取当前数据库事实。 */
  @Autowired org.mybatis.spring.SqlSessionTemplate sqlSession;

  @Test void departmentsIncludeDescendantsAndPositionsAndTagsUseIntersection() {
    long org=TenantContext.requireTenantId();
    jdbc.update("INSERT INTO us_sys_dept (organization_id,dept_code,dept_name,ancestors) VALUES (?,'b7parent','部门父','')",org);
    Long parent=jdbc.queryForObject("SELECT dept_id FROM us_sys_dept WHERE organization_id=? AND dept_code='b7parent'",Long.class,org);
    jdbc.update("INSERT INTO us_sys_dept (organization_id,dept_code,dept_name,ancestors,parent_id) VALUES (?,'b7child','部门子',?,?)",org,"0,"+parent,parent);
    Long child=jdbc.queryForObject("SELECT dept_id FROM us_sys_dept WHERE organization_id=? AND dept_code='b7child'",Long.class,org);
    jdbc.update("INSERT INTO sts_org_position (organization_id,position_code,position_name,description,status) VALUES (?,'b7position','电工','',1)",org);
    Long position=jdbc.queryForObject("SELECT position_id FROM sts_org_position WHERE organization_id=? AND position_code='b7position'",Long.class,org);
    jdbc.update("INSERT INTO sts_org_tag (organization_id,tag_name,description,status) VALUES (?,'b7tag','',1)",org);
    Long tag=jdbc.queryForObject("SELECT tag_id FROM sts_org_tag WHERE organization_id=? AND tag_name='b7tag'",Long.class,org);
    SysUser user=user("batch7_relation"); user.setDeptId(child); users.updateById(user);
    jdbc.update("INSERT INTO sts_org_user_position (organization_id,user_id,position_id,is_primary) VALUES (?,?,?,1)",org,user.getUserId(),position);
    jdbc.update("INSERT INTO sts_org_user_tag (organization_id,user_id,tag_id) VALUES (?,?,?)",org,user.getUserId(),tag);
    PersonClassificationCondition c=new PersonClassificationCondition();c.setDeptIds(List.of(parent));c.setPositionIds(List.of(position,position+10000));c.setTagIds(List.of(tag));
    assertThat(people.resolve(org,c,LocalDate.now())).containsExactly(user.getUserId());
    c.setTagIds(List.of(tag+10000));assertThat(people.resolve(org,c,LocalDate.now())).isEmpty();
    c.setTagIds(List.of(tag));jdbc.update("UPDATE sts_org_position SET status=0 WHERE position_id=?",position);
    sqlSession.clearCache();
    assertThat(people.resolve(org,c,LocalDate.now())).isEmpty();
  }

  @Test void trainingMembershipAndRewardsIgnoreWithdrawnAndDeletedRecords() {
    long org=TenantContext.requireTenantId();SysUser user=user("batch7_training");
    jdbc.update("INSERT INTO sts_training_plan (organization_id,plan_code,plan_name,training_type_id,training_nature,start_time,end_time,publish_status,status) VALUES (?,'b7fact','事实计划',1,'INITIAL',NOW(),DATE_ADD(NOW(), INTERVAL 1 DAY),'RUNNING',1)",org);
    Long plan=jdbc.queryForObject("SELECT training_plan_id FROM sts_training_plan WHERE organization_id=? AND plan_code='b7fact'",Long.class,org);
    jdbc.update("INSERT INTO sts_training_participant (organization_id,training_plan_id,user_id,participant_org_id,join_source,joined_time,participant_status,online_hours,offline_hours,result_status) VALUES (?,?,?,?,'ASSIGN',NOW(),'LEARNING',1,0,'INCOMPLETE')",org,plan,user.getUserId(),org);
    Long participant=jdbc.queryForObject("SELECT training_participant_id FROM sts_training_participant WHERE training_plan_id=? AND user_id=?",Long.class,plan,user.getUserId());
    jdbc.update("INSERT INTO sts_training_reward_penalty (organization_id,training_plan_id,training_participant_id,record_type,title,status) VALUES (?,?,?,'REWARD','优秀学员',1),(?,?,?,'PENALTY','已停用记录',0)",org,plan,participant,org,plan,participant);
    PersonClassificationCondition c=new PersonClassificationCondition();c.setTrainingPlanId(plan);
    assertThat(people.resolve(org,c,LocalDate.now())).containsExactly(user.getUserId());
    assertThat(people.trainingFacts(org,user.getUserId())).singleElement().satisfies(fact -> assertThat(fact.getRewardPenaltyTitles()).isEqualTo("优秀学员"));
    jdbc.update("UPDATE sts_training_participant SET participant_status='WITHDRAWN' WHERE training_participant_id=?",participant);
    sqlSession.clearCache();
    assertThat(people.resolve(org,c,LocalDate.now())).isEmpty();assertThat(people.trainingFacts(org,user.getUserId())).isEmpty();
    jdbc.update("UPDATE sts_training_participant SET participant_status='LEARNING',deleted=training_participant_id WHERE training_participant_id=?",participant);
    sqlSession.clearCache();
    assertThat(people.resolve(org,c,LocalDate.now())).isEmpty();
  }

  @Test void missingFeaturesDoNotMatchAndBirthdayBoundaryIsInclusive() {
    SysUser qualified = user("batch7_qualified"), missing = user("batch7_missing");
    profile(qualified.getUserId(), LocalDate.of(2000,10,11), "电工", "MALE");
    PersonClassificationCondition c = new PersonClassificationCondition(); c.setKeyword("batch7_");
    c.setMinAge(26); c.setMaxAge(26); c.setGenders(List.of("MALE","FEMALE")); c.setOccupation("电工");
    assertThat(people.resolve(TenantContext.requireTenantId(),c,LocalDate.of(2026,10,11))).containsExactly(qualified.getUserId());
    assertThat(people.resolve(TenantContext.requireTenantId(),c,LocalDate.of(2026,10,10))).isEmpty();
    c.setOccupation("焊工");
    assertThat(people.resolve(TenantContext.requireTenantId(),c,LocalDate.of(2026,10,11))).isEmpty();
    assertThat(missing.getUserId()).isNotEqualTo(qualified.getUserId());
  }
  @Test void profileClearingAndStaleWriteUseRealOptimisticLock() {
    SysUser user=user("batch7_clear"); PersonProfile p=profile(user.getUserId(),LocalDate.of(1990,1,1),"电工","MALE");
    PersonProfile stale = new PersonProfile(); stale.setProfileId(p.getProfileId()); stale.setVersion(0); stale.setOccupation("旧值");
    p.setBirthDate(null); p.setOccupation(null); assertThat(profiles.updateById(p)).isEqualTo(1);
    assertThat(profiles.updateById(stale)).isZero();
    PersonProfile loaded = profiles.selectById(p.getProfileId());
    assertThat(loaded.getBirthDate()).isNull(); assertThat(loaded.getOccupation()).isNull();
  }
  @Test void explicitForeignTenantParameterCannotBypassTenantPlugin() {
    SysUser user=user("batch7_tenant"); profile(user.getUserId(),LocalDate.of(1990,1,1),"电工","MALE");
    Long current = TenantContext.requireTenantId();
    try {
      TenantContext.setTenantId(current+1000);
      PersonClassificationCondition c=new PersonClassificationCondition();c.setKeyword("batch7_tenant");
      assertThat(people.resolve(current,c,LocalDate.of(2026,10,11))).isEmpty();
    } finally { TenantContext.setTenantId(current); }
  }
  private SysUser user(String code) {
    SysUser user=new SysUser();user.setUserCode(code);user.setUserName(code);user.setStatus(1);users.insert(user);return user;
  }
  private PersonProfile profile(Long userId, LocalDate birthday, String occupation,String gender) {
    PersonProfile p=new PersonProfile();p.setUserId(userId);p.setBirthDate(birthday);p.setOccupation(occupation);p.setGender(gender);
    profiles.insert(p);return profiles.selectById(p.getProfileId());
  }
}

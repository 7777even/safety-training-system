package com.sts.survey.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sts.admin.support.AbstractMysqlIntegrationTest;
import com.sts.admin.tenant.TenantContext;
import com.sts.survey.entity.Survey;
import com.sts.survey.entity.SurveyInstance;
import com.sts.survey.entity.SurveyParticipant;
import com.sts.survey.support.SurveyMapperItConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 问卷、发放实例与参与人的租户边界（Testcontainers 真 MySQL）。
 * <p>
 * 参与人与作答是评价计算的输入，跨租户可读即等于把别家员工的评价拉进自己的统计。
 */
@SpringBootTest(classes = SurveyMapperItConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class SurveyTenantBoundaryIT extends AbstractMysqlIntegrationTest {

  @Autowired
  private SurveyMapper surveyMapper;
  @Autowired
  private SurveyInstanceMapper instanceMapper;
  @Autowired
  private SurveyParticipantMapper participantMapper;
  @Autowired
  private JdbcTemplate jdbcTemplate;

  private Long otherTenantId() {
    jdbcTemplate.update(
      "INSERT INTO sts_org_organization (org_code, org_name, org_type, status, description)"
        + " VALUES ('SV-OTHER', '另一家企业', 'ENTERPRISE', 1, '')");
    return jdbcTemplate.queryForObject(
      "SELECT organization_id FROM sts_org_organization WHERE org_code = 'SV-OTHER'", Long.class);
  }

  private Long insertSurvey(String code) {
    Survey survey = new Survey();
    survey.setSurveyCode(code);
    survey.setSurveyName("问卷-" + code);
    survey.setSurveyType("EVALUATION");
    survey.setIsAnonymous(0);
    survey.setContentStatus("DRAFT");
    survey.setStatus(1);
    surveyMapper.insert(survey);
    return survey.getSurveyId();
  }

  private Long insertInstance(Long surveyId, String code) {
    SurveyInstance instance = new SurveyInstance();
    instance.setInstanceCode(code);
    instance.setInstanceName("发放-" + code);
    instance.setSurveyId(surveyId);
    instance.setSurveyVersionId(1L);
    instance.setSourceType("STANDALONE");
    instance.setSourceId(0L);
    instance.setPublishMode("IMMEDIATE");
    instance.setIsAnonymousSnapshot(0);
    instance.setInstanceStatus("DRAFT");
    instance.setStatus(1);
    instanceMapper.insert(instance);
    return instance.getSurveyInstanceId();
  }

  private Long insertParticipant(Long instanceId, Long surveyId, Long userId) {
    SurveyParticipant p = new SurveyParticipant();
    p.setSurveyInstanceId(instanceId);
    p.setSurveyId(surveyId);
    p.setSurveyVersionId(1L);
    p.setUserId(userId);
    p.setParticipantStatus("PENDING");
    p.setFirstOpenTime(LocalDateTime.now());
    participantMapper.insert(p);
    return p.getSurveyParticipantId();
  }

  @Test
  @DisplayName("跨租户按 ID 直查问卷、发放实例、参与人一律返回空")
  void selectById_isRejectedAcrossTenants() {
    Long other = otherTenantId();
    Long surveyId = TenantContext.callWithTenant(other, () -> insertSurvey("SV-CROSS"));
    Long instanceId = TenantContext.callWithTenant(other, () -> insertInstance(surveyId, "SV-INST-CROSS"));
    Long participantId = TenantContext.callWithTenant(other,
                                                      () -> insertParticipant(instanceId, surveyId, 4001L));

    assertThat(surveyMapper.selectById(surveyId)).isNull();
    assertThat(instanceMapper.selectById(instanceId)).isNull();
    assertThat(participantMapper.selectById(participantId)).isNull();
  }

  @Test
  @DisplayName("按发放实例 ID 列参与人时，别家实例的参与人不会混入")
  void listParticipantsByInstanceId_isTenantScoped() {
    Long other = otherTenantId();
    Long surveyId = TenantContext.callWithTenant(other, () -> insertSurvey("SV-LIST"));
    Long instanceId = TenantContext.callWithTenant(other, () -> insertInstance(surveyId, "SV-INST-LIST"));
    TenantContext.runWithTenant(other, () -> insertParticipant(instanceId, surveyId, 4002L));

    assertThat(participantMapper.selectList(new LambdaQueryWrapper<SurveyParticipant>()
                                              .eq(SurveyParticipant::getSurveyInstanceId, instanceId))).isEmpty();
    assertThat(participantMapper.selectCount(new LambdaQueryWrapper<SurveyParticipant>()
                                               .eq(SurveyParticipant::getSurveyInstanceId, instanceId)))
      .describedAs("count 必须与列表一致，否则评价计算会把别家参与人算进分母").isZero();
  }

  @Test
  @DisplayName("跨租户关闭发放实例影响 0 行")
  void updateById_cannotCloseOtherTenantInstance() {
    Long other = otherTenantId();
    Long surveyId = TenantContext.callWithTenant(other, () -> insertSurvey("SV-UPD"));
    Long instanceId = TenantContext.callWithTenant(other, () -> insertInstance(surveyId, "SV-INST-UPD"));

    SurveyInstance tampered = new SurveyInstance();
    tampered.setSurveyInstanceId(instanceId);
    tampered.setInstanceStatus("CLOSED");
    assertThat(instanceMapper.updateById(tampered)).isZero();

    String stored = jdbcTemplate.queryForObject(
      "SELECT instance_status FROM sts_survey_instance WHERE survey_instance_id = ?", String.class, instanceId);
    assertThat(stored).isEqualTo("DRAFT");
  }

  @Test
  @DisplayName("问卷编码在不同租户之间可以重名")
  void surveyCode_isUniqueOnlyWithinTenant() {
    Long other = otherTenantId();
    insertSurvey("SV-SAME-CODE");

    Long otherSurveyId = TenantContext.callWithTenant(other, () -> insertSurvey("SV-SAME-CODE"));

    assertThat(otherSurveyId).isNotNull();
    assertThat(surveyMapper.selectList(new LambdaQueryWrapper<Survey>()
                                         .eq(Survey::getSurveyCode, "SV-SAME-CODE"))).hasSize(1);
  }
}

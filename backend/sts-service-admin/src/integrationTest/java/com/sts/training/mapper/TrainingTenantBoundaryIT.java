package com.sts.training.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sts.admin.support.AbstractMysqlIntegrationTest;
import com.sts.admin.tenant.TenantContext;
import com.sts.training.entity.*;
import com.sts.training.support.TrainingMapperItConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 培训计划与执行数据的租户边界（Testcontainers 真 MySQL）。
 * <p>
 * 计划、参训人、成绩是档案与证书的事实来源，一旦按 ID 能跨租户读到或改到，
 * 后面的归档和签发就会把别家数据算进来。
 */
@SpringBootTest(classes = TrainingMapperItConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class TrainingTenantBoundaryIT extends AbstractMysqlIntegrationTest {

  @Autowired
  private TrainingPlanMapper planMapper;
  @Autowired
  private TrainingTypeMapper typeMapper;
  @Autowired
  private TrainingStandardMapper standardMapper;
  @Autowired
  private TrainingTemplateMapper templateMapper;
  @Autowired
  private TrainingParticipantMapper participantMapper;
  @Autowired
  private JdbcTemplate jdbcTemplate;

  private Long otherTenantId() {
    jdbcTemplate.update(
      "INSERT INTO sts_org_organization (org_code, org_name, org_type, status, description)"
        + " VALUES ('TR-OTHER', '另一家企业', 'ENTERPRISE', 1, '')");
    return jdbcTemplate.queryForObject(
      "SELECT organization_id FROM sts_org_organization WHERE org_code = 'TR-OTHER'", Long.class);
  }

  private Long insertType(String code) {
    TrainingType type = new TrainingType();
    type.setTypeCode(code);
    type.setTypeName("类型-" + code);
    type.setStatus(1);
    type.setDescription("IT");
    typeMapper.insert(type);
    return type.getTrainingTypeId();
  }

  private Long insertStandard(Long typeId, String code) {
    TrainingStandard standard = new TrainingStandard();
    standard.setStandardCode(code);
    standard.setStandardName("标准-" + code);
    standard.setTrainingTypeId(typeId);
    standard.setStandardVersion("V1");
    standard.setPublishStatus("PUBLISHED");
    standard.setStatus(1);
    standardMapper.insert(standard);
    return standard.getTrainingStandardId();
  }

  private Long insertTemplate(Long typeId, Long standardId, String code) {
    TrainingTemplate template = new TrainingTemplate();
    template.setTemplateCode(code);
    template.setTemplateName("模板-" + code);
    template.setTrainingTypeId(typeId);
    template.setTrainingStandardId(standardId);
    template.setPublishStatus("PUBLISHED");
    template.setStatus(1);
    templateMapper.insert(template);
    return template.getTrainingTemplateId();
  }

  private Long insertPlan(Long typeId, String code) {
    TrainingPlan plan = new TrainingPlan();
    plan.setPlanCode(code);
    plan.setPlanName("计划-" + code);
    plan.setTrainingTypeId(typeId);
    plan.setTrainingNature("INITIAL");
    plan.setStartTime(LocalDateTime.now());
    plan.setEndTime(LocalDateTime.now().plusDays(1));
    plan.setPublishStatus("DRAFT");
    plan.setStatus(1);
    planMapper.insert(plan);
    return plan.getTrainingPlanId();
  }

  private Long insertParticipant(Long planId, Long userId) {
    TrainingParticipant p = new TrainingParticipant();
    p.setTrainingPlanId(planId);
    p.setUserId(userId);
    // participant_org_id 记录参训人自身归属，与计划租户一致
    p.setParticipantOrgId(TenantContext.requireTenantId());
    p.setJoinSource("ASSIGN");
    p.setJoinedTime(LocalDateTime.now());
    p.setParticipantStatus("PENDING");
    p.setOnlineHours(BigDecimal.ZERO);
    p.setOfflineHours(BigDecimal.ZERO);
    participantMapper.insert(p);
    return p.getTrainingParticipantId();
  }

  @Test
  @DisplayName("跨租户按 ID 直查培训类型、标准、模板、计划、参训人一律返回空")
  void selectById_isRejectedAcrossTenants() {
    Long other = otherTenantId();
    Long typeId = TenantContext.callWithTenant(other, () -> insertType("TR-TYPE"));
    Long standardId = TenantContext.callWithTenant(other, () -> insertStandard(typeId, "TR-STD"));
    Long templateId = TenantContext.callWithTenant(
      other, () -> insertTemplate(typeId, standardId, "TR-TEMPLATE"));
    Long planId = TenantContext.callWithTenant(other, () -> insertPlan(typeId, "TR-PLAN"));
    Long participantId = TenantContext.callWithTenant(other, () -> insertParticipant(planId, 7001L));

    assertThat(typeMapper.selectById(typeId)).isNull();
    assertThat(standardMapper.selectById(standardId)).isNull();
    assertThat(templateMapper.selectById(templateId)).isNull();
    assertThat(planMapper.selectById(planId)).isNull();
    assertThat(participantMapper.selectById(participantId)).isNull();
  }

  @Test
  @DisplayName("跨租户改写培训计划影响 0 行：发布与成绩写不到别家计划")
  void updateById_cannotTouchOtherTenantPlan() {
    Long other = otherTenantId();
    Long typeId = TenantContext.callWithTenant(other, () -> insertType("TR-UPD-TYPE"));
    Long planId = TenantContext.callWithTenant(other, () -> insertPlan(typeId, "TR-UPD-PLAN"));

    TrainingPlan tampered = new TrainingPlan();
    tampered.setTrainingPlanId(planId);
    tampered.setPublishStatus("PUBLISHED");
    assertThat(planMapper.updateById(tampered)).isZero();

    String stored = jdbcTemplate.queryForObject(
      "SELECT publish_status FROM sts_training_plan WHERE training_plan_id = ?", String.class, planId);
    assertThat(stored).isEqualTo("DRAFT");
  }

  @Test
  @DisplayName("按计划 ID 列参训人时，别家计划的参训人不会混入")
  void listParticipantsByPlanId_isTenantScoped() {
    Long other = otherTenantId();
    Long typeId = TenantContext.callWithTenant(other, () -> insertType("TR-LIST-TYPE"));
    Long planId = TenantContext.callWithTenant(other, () -> insertPlan(typeId, "TR-LIST-PLAN"));
    TenantContext.runWithTenant(other, () -> insertParticipant(planId, 7002L));

    assertThat(participantMapper.selectList(new LambdaQueryWrapper<TrainingParticipant>()
                                              .eq(TrainingParticipant::getTrainingPlanId, planId))).isEmpty();
  }

  @Test
  @DisplayName("计划编码在不同租户之间可以重名")
  void planCode_isUniqueOnlyWithinTenant() {
    Long other = otherTenantId();
    Long ownTypeId = insertType("TR-SAME-TYPE");
    insertPlan(ownTypeId, "TR-SAME-CODE");

    Long otherTypeId = TenantContext.callWithTenant(other, () -> insertType("TR-SAME-TYPE"));
    Long otherPlanId = TenantContext.callWithTenant(other, () -> insertPlan(otherTypeId, "TR-SAME-CODE"));

    assertThat(otherPlanId).isNotNull();
    assertThat(planMapper.selectList(new LambdaQueryWrapper<TrainingPlan>()
                                       .eq(TrainingPlan::getPlanCode, "TR-SAME-CODE"))).hasSize(1);
  }
}

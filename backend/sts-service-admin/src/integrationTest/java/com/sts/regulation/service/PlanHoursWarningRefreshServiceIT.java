package com.sts.regulation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sts.admin.support.AbstractMysqlIntegrationTest;
import com.sts.admin.tenant.TenantContext;
import com.sts.org.mapper.OrganizationMapper;
import com.sts.org.service.TenantDirectory;
import com.sts.regulation.entity.RegulationWarning;
import com.sts.regulation.entity.RegulationWarningEvidence;
import com.sts.regulation.mapper.RegulationWarningEvidenceMapper;
import com.sts.regulation.mapper.RegulationWarningMapper;
import com.sts.regulation.mapper.RegulationWarningRuleMapper;
import com.sts.regulation.support.RegulationMapperItConfig;
import com.sts.training.entity.TrainingPlan;
import com.sts.training.entity.TrainingPlanContent;
import com.sts.training.entity.TrainingPlanStage;
import com.sts.training.entity.TrainingType;
import com.sts.training.mapper.TrainingPlanContentMapper;
import com.sts.training.mapper.TrainingPlanMapper;
import com.sts.training.mapper.TrainingPlanStageMapper;
import com.sts.training.mapper.TrainingTypeMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = RegulationMapperItConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class PlanHoursWarningRefreshServiceIT extends AbstractMysqlIntegrationTest {

  @Autowired
  private RegulationWarningRuleMapper ruleMapper;
  @Autowired
  private RegulationWarningMapper warningMapper;
  @Autowired
  private RegulationWarningEvidenceMapper evidenceMapper;
  @Autowired
  private OrganizationMapper organizationMapper;
  @Autowired
  private TrainingPlanMapper planMapper;
  @Autowired
  private TrainingPlanStageMapper stageMapper;
  @Autowired
  private TrainingPlanContentMapper contentMapper;
  @Autowired
  private TrainingTypeMapper typeMapper;

  @Test
  @DisplayName("初培学时不足自动生成待查看预警，重复刷新不新增，补足学时后结束")
  void refresh_createsUnreadWarningIdempotentlyThenEndsWhenHoursMet() {
    Long demoId = organizationMapper.selectByOrgCode("DEMO").getOrganizationId();
    TrainingType type = new TrainingType();
    type.setTypeCode("IT-M5-HOURS");
    type.setTypeName("学时预警类型");
    type.setInitialHours(new BigDecimal("80"));
    type.setReviewHours(new BigDecimal("24"));
    type.setStatus(1);
    type.setDescription("IT");
    typeMapper.insert(type);

    TrainingPlan plan = new TrainingPlan();
    plan.setPlanCode("IT-M5-PLAN");
    plan.setPlanName("初培学时不足");
    plan.setTrainingTypeId(type.getTrainingTypeId());
    plan.setTrainingNature("INITIAL");
    plan.setStartTime(LocalDateTime.now());
    plan.setEndTime(LocalDateTime.now().plusDays(1));
    plan.setPublishStatus("DRAFT");
    plan.setStatus(1);
    planMapper.insert(plan);
    insertCourseHours(plan.getTrainingPlanId(), new BigDecimal("60"));

    PlanHoursWarningRefreshService service = newService();
    service.refresh(plan.getTrainingPlanId());
    service.refresh(plan.getTrainingPlanId());

    Long regulatorId = organizationMapper.selectByOrgCode("REGULATOR").getOrganizationId();
    TenantContext.runWithTenant(regulatorId, () -> {
      assertThat(warningMapper.selectCount(new LambdaQueryWrapper<RegulationWarning>()
                                             .eq(RegulationWarning::getTrainingPlanId, plan.getTrainingPlanId())
                                             .eq(RegulationWarning::getWarningStatus, "UNREAD"))).isEqualTo(1);
      RegulationWarningEvidence evidence = evidenceMapper.selectOne(
        new LambdaQueryWrapper<RegulationWarningEvidence>()
          .eq(RegulationWarningEvidence::getSourceId, plan.getTrainingPlanId()));
      assertThat(evidence.getActualValue()).isEqualByComparingTo("60");
      assertThat(evidence.getThresholdValue()).isEqualByComparingTo("80");
    });

    TenantContext.runWithTenant(demoId, () -> {
      TrainingPlanStage stage = stageMapper.selectOne(
        new LambdaQueryWrapper<TrainingPlanStage>().eq(TrainingPlanStage::getTrainingPlanId, plan.getTrainingPlanId()));
      TrainingPlanContent content = contentMapper.selectOne(
        new LambdaQueryWrapper<TrainingPlanContent>().eq(TrainingPlanContent::getPlanStageId, stage.getPlanStageId()));
      content.setRequiredHours(new BigDecimal("80"));
      contentMapper.updateById(content);
    });
    service.refresh(plan.getTrainingPlanId());
    TenantContext.runWithTenant(regulatorId, () ->
      assertThat(warningMapper.selectCount(new LambdaQueryWrapper<RegulationWarning>()
                                             .eq(RegulationWarning::getTrainingPlanId, plan.getTrainingPlanId())
                                             .eq(RegulationWarning::getWarningStatus, "ENDED"))).isEqualTo(1));
  }

  private void insertCourseHours(Long planId, BigDecimal hours) {
    TrainingPlanStage stage = new TrainingPlanStage();
    stage.setTrainingPlanId(planId);
    stage.setStageName("线上阶段");
    stage.setStageType("ONLINE");
    stage.setSortOrder(1);
    stage.setStageStatus("PENDING");
    stageMapper.insert(stage);
    TrainingPlanContent content = new TrainingPlanContent();
    content.setPlanStageId(stage.getPlanStageId());
    content.setContentType("COURSE");
    content.setContentName("课程学时");
    content.setIsRequired(1);
    content.setRequiredHours(hours);
    content.setSortOrder(1);
    content.setContentStatus("PENDING");
    contentMapper.insert(content);
  }

  private PlanHoursWarningRefreshService newService() {
    return new PlanHoursWarningRefreshService(
      new TenantDirectory(organizationMapper),
      planMapper, stageMapper, contentMapper, typeMapper, organizationMapper,
      ruleMapper, warningMapper, evidenceMapper, new ObjectMapper());
  }
}

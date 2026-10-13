package com.sts.regulation.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sts.org.mapper.OrganizationMapper;
import com.sts.org.service.TenantDirectory;
import com.sts.regulation.entity.RegulationWarning;
import com.sts.regulation.entity.RegulationWarningEvidence;
import com.sts.regulation.entity.RegulationWarningRule;
import com.sts.regulation.mapper.RegulationWarningEvidenceMapper;
import com.sts.regulation.mapper.RegulationWarningMapper;
import com.sts.regulation.mapper.RegulationWarningRuleMapper;
import com.sts.training.entity.TrainingPlan;
import com.sts.training.entity.TrainingPlanContent;
import com.sts.training.entity.TrainingPlanStage;
import com.sts.training.entity.TrainingType;
import com.sts.training.mapper.TrainingPlanContentMapper;
import com.sts.training.mapper.TrainingPlanMapper;
import com.sts.training.mapper.TrainingPlanStageMapper;
import com.sts.training.mapper.TrainingTypeMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.isNull;

class PlanHoursWarningRefreshServiceTest {

  @BeforeAll
  static void initTableInfo() {
    MybatisConfiguration configuration = new MybatisConfiguration();
    MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
    TableInfoHelper.initTableInfo(assistant, RegulationWarning.class);
    TableInfoHelper.initTableInfo(assistant, RegulationWarningRule.class);
    TableInfoHelper.initTableInfo(assistant, TrainingPlan.class);
    TableInfoHelper.initTableInfo(assistant, TrainingPlanStage.class);
    TableInfoHelper.initTableInfo(assistant, TrainingPlanContent.class);
    TableInfoHelper.initTableInfo(assistant, TrainingType.class);
  }

  private TenantDirectory tenantDirectory;
  private TrainingPlanMapper planMapper;
  private PlanHoursWarningRefreshService service;

  @BeforeEach
  void setUp() {
    tenantDirectory = mock(TenantDirectory.class);
    planMapper = mock(TrainingPlanMapper.class);
    service = new PlanHoursWarningRefreshService(
      tenantDirectory, planMapper, mock(TrainingPlanStageMapper.class), mock(TrainingPlanContentMapper.class),
      mock(TrainingTypeMapper.class), mock(OrganizationMapper.class), mock(RegulationWarningRuleMapper.class),
      mock(RegulationWarningMapper.class), mock(RegulationWarningEvidenceMapper.class), new ObjectMapper());
  }

  @Test
  @DisplayName("无监管租户时跳过刷新")
  void refresh_skipsWhenNoRegulator() {
    when(tenantDirectory.findRegulatorOrganizationId()).thenReturn(null);
    service.refresh(9L);
    verify(planMapper, never()).selectById(any());
  }

  @Test
  @DisplayName("已结束预警再次不足时清空上轮查看和结束时间")
  void refresh_reopenedWarningClearsPreviousTimes() {
    when(tenantDirectory.findRegulatorOrganizationId()).thenReturn(1L);
    TrainingPlan plan = new TrainingPlan();
    plan.setTrainingPlanId(9L);
    plan.setOrganizationId(2L);
    plan.setStatus(1);
    plan.setPublishStatus("DRAFT");
    plan.setTrainingNature("INITIAL");
    plan.setTrainingTypeId(28L);
    plan.setPlanName("验收计划");
    when(planMapper.selectById(9L)).thenReturn(plan);
    TrainingType type = new TrainingType();
    type.setInitialHours(BigDecimal.valueOf(5));
    var typeMapper = mock(TrainingTypeMapper.class);
    when(typeMapper.selectById(28L)).thenReturn(type);
    var stageMapper = mock(TrainingPlanStageMapper.class);
    when(stageMapper.selectList(any())).thenReturn(List.of());
    RegulationWarning warning = new RegulationWarning();
    warning.setWarningId(3L);
    warning.setWarningStatus(PlanHoursWarningRefreshService.STATUS_ENDED);
    warning.setFirstViewedTime(LocalDateTime.now().minusDays(1));
    warning.setClosedTime(LocalDateTime.now().minusHours(1));
    var warningMapper = mock(RegulationWarningMapper.class);
    when(warningMapper.selectOne(any())).thenReturn(warning);
    var ruleMapper = mock(RegulationWarningRuleMapper.class);
    when(ruleMapper.selectOne(any())).thenReturn(new RegulationWarningRule());
    var reopenedService = new PlanHoursWarningRefreshService(tenantDirectory, planMapper, stageMapper,
      mock(TrainingPlanContentMapper.class), typeMapper, mock(OrganizationMapper.class), ruleMapper,
      warningMapper, mock(RegulationWarningEvidenceMapper.class), new ObjectMapper());

    reopenedService.refresh(9L);

    verify(warningMapper).update(isNull(), any());
  }
}

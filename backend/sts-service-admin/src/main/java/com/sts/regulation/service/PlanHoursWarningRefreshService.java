package com.sts.regulation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sts.admin.tenant.TenantContext;
import com.sts.org.entity.Organization;
import com.sts.org.mapper.OrganizationMapper;
import com.sts.org.service.TenantDirectory;
import com.sts.regulation.entity.RegulationWarning;
import com.sts.regulation.entity.RegulationWarningEvidence;
import com.sts.regulation.entity.RegulationWarningRule;
import com.sts.regulation.mapper.RegulationWarningEvidenceMapper;
import com.sts.regulation.mapper.RegulationWarningMapper;
import com.sts.regulation.mapper.RegulationWarningRuleMapper;
import com.sts.regulation.util.WarningDedupUtil;
import com.sts.training.entity.TrainingPlan;
import com.sts.training.entity.TrainingPlanContent;
import com.sts.training.entity.TrainingPlanStage;
import com.sts.training.entity.TrainingType;
import com.sts.training.mapper.TrainingPlanContentMapper;
import com.sts.training.mapper.TrainingPlanMapper;
import com.sts.training.mapper.TrainingPlanStageMapper;
import com.sts.training.mapper.TrainingTypeMapper;
import com.sts.training.service.TrainingArrangedHours;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 培训计划学时不足预警：计划保存后自动刷新，不向用户暴露检测入口。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PlanHoursWarningRefreshService {

  public static final String RULE_CODE = "PLAN_HOURS_INSUFFICIENT";
  public static final String METRIC_CODE = "PLAN_TOTAL_HOURS";
  public static final String CYCLE_CURRENT = "CURRENT";
  public static final String STATUS_UNREAD = "UNREAD";
  public static final String STATUS_READ = "READ";
  public static final String STATUS_ENDED = "ENDED";

  private final TenantDirectory tenantDirectory;
  private final TrainingPlanMapper trainingPlanMapper;
  private final TrainingPlanStageMapper planStageMapper;
  private final TrainingPlanContentMapper planContentMapper;
  private final TrainingTypeMapper trainingTypeMapper;
  private final OrganizationMapper organizationMapper;
  private final RegulationWarningRuleMapper ruleMapper;
  private final RegulationWarningMapper warningMapper;
  private final RegulationWarningEvidenceMapper evidenceMapper;
  private final ObjectMapper objectMapper;

  @Transactional
  public void refresh(Long trainingPlanId) {
    if (trainingPlanId == null) {
      return;
    }
    Long regulatorOrgId = TenantContext.callWithBypass(tenantDirectory::findRegulatorOrganizationId);
    if (regulatorOrgId == null) {
      log.warn("无监管租户，跳过学时预警刷新 planId={}", trainingPlanId);
      return;
    }
    TrainingPlan plan = TenantContext.callWithBypass(() -> trainingPlanMapper.selectById(trainingPlanId));
    if (plan == null || !Integer.valueOf(1).equals(plan.getStatus())
      || "CANCELLED".equals(plan.getPublishStatus())) {
      endCurrent(regulatorOrgId, trainingPlanId, plan == null ? null : plan.getOrganizationId());
      return;
    }
    HoursCompare compare = TenantContext.callWithTenant(plan.getOrganizationId(), () -> evaluate(plan));
    if (compare == null || !compare.insufficient) {
      endCurrent(regulatorOrgId, trainingPlanId, plan.getOrganizationId());
      return;
    }
    upsertWarning(regulatorOrgId, plan, compare);
  }

  @Transactional
  public void refreshAllActivePlans() {
    Long regulatorOrgId = TenantContext.callWithBypass(tenantDirectory::findRegulatorOrganizationId);
    if (regulatorOrgId == null) {
      return;
    }
    List<TrainingPlan> plans = TenantContext.callWithBypass(() -> trainingPlanMapper.selectList(
      new LambdaQueryWrapper<TrainingPlan>()
        .eq(TrainingPlan::getStatus, 1)
        .in(TrainingPlan::getPublishStatus, "DRAFT", "PUBLISHED", "RUNNING", "ENDED")));
    for (TrainingPlan plan : plans) {
      refresh(plan.getTrainingPlanId());
    }
  }

  private HoursCompare evaluate(TrainingPlan plan) {
    if (!"INITIAL".equals(plan.getTrainingNature()) && !"REVIEW".equals(plan.getTrainingNature())) {
      return null;
    }
    if (plan.getTrainingTypeId() == null) {
      return null;
    }
    TrainingType type = trainingTypeMapper.selectById(plan.getTrainingTypeId());
    if (type == null) {
      return null;
    }
    BigDecimal required = "INITIAL".equals(plan.getTrainingNature()) ? type.getInitialHours() : type.getReviewHours();
    if (required == null) {
      return null;
    }
    BigDecimal actual = sumPlanHours(plan.getTrainingPlanId());
    HoursCompare compare = new HoursCompare();
    compare.actual = actual;
    compare.required = required;
    compare.gap = required.subtract(actual);
    compare.insufficient = actual.compareTo(required) < 0;
    compare.type = type;
    return compare;
  }

  private BigDecimal sumPlanHours(Long planId) {
    List<Long> stageIds = planStageMapper.selectList(
        new LambdaQueryWrapper<TrainingPlanStage>()
          .eq(TrainingPlanStage::getTrainingPlanId, planId)
          .select(TrainingPlanStage::getPlanStageId))
      .stream()
      .map(TrainingPlanStage::getPlanStageId)
      .toList();
    if (stageIds.isEmpty()) {
      return BigDecimal.ZERO;
    }
    BigDecimal total = BigDecimal.ZERO;
    for (TrainingPlanContent content : planContentMapper.selectList(
      new LambdaQueryWrapper<TrainingPlanContent>().in(TrainingPlanContent::getPlanStageId, stageIds))) {
      total = total.add(TrainingArrangedHours.contributingHours(content.getContentType(), content.getRequiredHours()));
    }
    return total;
  }

  private void upsertWarning(Long regulatorOrgId, TrainingPlan plan, HoursCompare compare) {
    Long regulatedOrgId = plan.getOrganizationId();
    String dedupKey = WarningDedupUtil.hoursKey(regulatedOrgId, plan.getTrainingPlanId());
    TenantContext.runWithTenant(regulatorOrgId, () -> {
      RegulationWarningRule rule = requireInternalRule();
      RegulationWarning warning = warningMapper.selectOne(
        new LambdaQueryWrapper<RegulationWarning>().eq(RegulationWarning::getDedupKey, dedupKey));
      LocalDateTime now = LocalDateTime.now();
      if (warning == null) {
        warning = new RegulationWarning();
        warning.setWarningRuleId(rule.getWarningRuleId());
        warning.setRegulatedOrgId(regulatedOrgId);
        warning.setTrainingPlanId(plan.getTrainingPlanId());
        warning.setDetectionCycle(CYCLE_CURRENT);
        warning.setDedupKey(dedupKey);
        warning.setWarningLevel("WARN");
        warning.setWarningTitle(title(plan, compare));
        warning.setWarningStatus(STATUS_UNREAD);
        warning.setDetectedTime(now);
        try {
          warningMapper.insert(warning);
        } catch (DuplicateKeyException ex) {
          warning = warningMapper.selectOne(
            new LambdaQueryWrapper<RegulationWarning>().eq(RegulationWarning::getDedupKey, dedupKey));
        }
      } else {
        boolean reopened = STATUS_ENDED.equals(warning.getWarningStatus());
        if (reopened) {
          warning.setWarningStatus(STATUS_UNREAD);
          warning.setClosedTime(null);
          warning.setFirstViewedTime(null);
        }
        warning.setWarningTitle(title(plan, compare));
        warning.setDetectedTime(now);
        warningMapper.updateById(warning);
        if (reopened) {
          // MyBatis-Plus 的普通实体更新会忽略 null，需显式清空上一轮留痕。
          warningMapper.update(null, Wrappers.<RegulationWarning>lambdaUpdate()
            .eq(RegulationWarning::getWarningId, warning.getWarningId())
            .set(RegulationWarning::getFirstViewedTime, null)
            .set(RegulationWarning::getClosedTime, null));
        }
      }
      if (warning != null && warning.getWarningId() != null) {
        upsertEvidence(warning.getWarningId(), plan, compare);
      }
    });
  }

  private void endCurrent(Long regulatorOrgId, Long trainingPlanId, Long regulatedOrgId) {
    TenantContext.runWithTenant(regulatorOrgId, () -> {
      LambdaQueryWrapper<RegulationWarning> wrapper = new LambdaQueryWrapper<RegulationWarning>()
        .eq(RegulationWarning::getTrainingPlanId, trainingPlanId)
        .in(RegulationWarning::getWarningStatus, STATUS_UNREAD, STATUS_READ);
      if (regulatedOrgId != null) {
        wrapper.eq(RegulationWarning::getRegulatedOrgId, regulatedOrgId);
      }
      for (RegulationWarning warning : warningMapper.selectList(wrapper)) {
        warning.setWarningStatus(STATUS_ENDED);
        warning.setClosedTime(LocalDateTime.now());
        warningMapper.updateById(warning);
      }
    });
  }

  private RegulationWarningRule requireInternalRule() {
    RegulationWarningRule rule = ruleMapper.selectOne(
      new LambdaQueryWrapper<RegulationWarningRule>()
        .eq(RegulationWarningRule::getRuleCode, RULE_CODE)
        .eq(RegulationWarningRule::getStatus, 1)
        .last("LIMIT 1"));
    if (rule != null) {
      return rule;
    }
    rule = new RegulationWarningRule();
    rule.setRuleCode(RULE_CODE);
    rule.setRuleName("培训计划学时不足");
    rule.setWarningType("PLAN_TOTAL_HOURS");
    rule.setWarningLevel("WARN");
    rule.setStatus(1);
    rule.setDescription("【内部保留】固定学时不足规则，不提供管理接口");
    ruleMapper.insert(rule);
    return rule;
  }

  private void upsertEvidence(Long warningId, TrainingPlan plan, HoursCompare compare) {
    String evidenceKey = WarningDedupUtil.evidenceKey(METRIC_CODE, "TRAINING_PLAN", plan.getTrainingPlanId());
    Map<String, Object> snap = new LinkedHashMap<>();
    snap.put("planId", plan.getTrainingPlanId());
    snap.put("planCode", plan.getPlanCode());
    snap.put("planName", plan.getPlanName());
    snap.put("regulatedOrgId", plan.getOrganizationId());
    Organization org = TenantContext.callWithBypass(() -> organizationMapper.selectById(plan.getOrganizationId()));
    snap.put("regulatedOrgName", org == null ? null : org.getOrgName());
    snap.put("trainingTypeId", plan.getTrainingTypeId());
    snap.put("trainingTypeName", compare.type.getTypeName());
    snap.put("trainingNature", plan.getTrainingNature());
    snap.put("planTotalHours", compare.actual);
    snap.put("requiredHours", compare.required);
    snap.put("hoursGap", compare.gap);
    snap.put("detectedTime", LocalDateTime.now().toString());
    String snapJson;
    try {
      snapJson = objectMapper.writeValueAsString(snap);
    } catch (Exception ex) {
      snapJson = "{}";
    }
    RegulationWarningEvidence existing = evidenceMapper.selectOne(
      new LambdaQueryWrapper<RegulationWarningEvidence>()
        .eq(RegulationWarningEvidence::getWarningId, warningId)
        .eq(RegulationWarningEvidence::getEvidenceKey, evidenceKey));
    if (existing == null) {
      RegulationWarningEvidence evidence = new RegulationWarningEvidence();
      evidence.setWarningId(warningId);
      evidence.setEvidenceKey(evidenceKey);
      evidence.setMetricCode(METRIC_CODE);
      evidence.setActualValue(compare.actual);
      evidence.setThresholdValue(compare.required);
      evidence.setUnit("HOURS");
      evidence.setSourceType("TRAINING_PLAN");
      evidence.setSourceId(plan.getTrainingPlanId());
      evidence.setEvidenceSnapshotJson(snapJson);
      try {
        evidenceMapper.insert(evidence);
      } catch (DuplicateKeyException ignored) {
        // idempotent
      }
    } else {
      existing.setActualValue(compare.actual);
      existing.setThresholdValue(compare.required);
      existing.setEvidenceSnapshotJson(snapJson);
      evidenceMapper.updateById(existing);
    }
  }

  private static String title(TrainingPlan plan, HoursCompare compare) {
    return "学时不足 - " + plan.getPlanName() + "（差 " + compare.gap.stripTrailingZeros().toPlainString() + " 学时）";
  }

  private static final class HoursCompare {
    private boolean insufficient;
    private BigDecimal actual;
    private BigDecimal required;
    private BigDecimal gap;
    private TrainingType type;
  }
}

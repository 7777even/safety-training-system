package com.sts.training.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sts.admin.support.AbstractMysqlIntegrationTest;
import com.sts.common.exception.BusinessException;
import com.sts.evaluation.entity.EvaluationForm;
import com.sts.evaluation.entity.EvaluationFormVersion;
import com.sts.evaluation.mapper.EvaluationFormMapper;
import com.sts.evaluation.mapper.EvaluationFormVersionMapper;
import com.sts.training.dto.*;
import com.sts.training.entity.TrainingPlan;
import com.sts.training.entity.TrainingPlanContent;
import com.sts.training.entity.TrainingPlanStage;
import com.sts.training.mapper.TrainingPlanContentMapper;
import com.sts.training.mapper.TrainingPlanEvaluationFormMapper;
import com.sts.training.mapper.TrainingPlanMapper;
import com.sts.training.mapper.TrainingPlanStageMapper;
import com.sts.training.support.TrainingMixedPlanItConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = TrainingMixedPlanItConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class TrainingPlanServiceImplIT extends AbstractMysqlIntegrationTest {

  @Autowired
  private TrainingPlanService planService;
  @Autowired
  private TrainingPlanMapper planMapper;
  @Autowired
  private TrainingPlanStageMapper stageMapper;
  @Autowired
  private TrainingPlanContentMapper contentMapper;
  @Autowired
  private TrainingPlanEvaluationFormMapper planEvaluationFormMapper;
  @Autowired
  private EvaluationFormMapper evaluationFormMapper;
  @Autowired
  private EvaluationFormVersionMapper evaluationFormVersionMapper;
  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Test
  @DisplayName("replaceStages：旧计划阶段及内容软删后仅保留本次提交的数据")
  void replaceStages_softDeletesExistingStageAndContent() {
    Long organizationId = demoTenantId();
    Long trainingTypeId = jdbcTemplate.queryForObject(
      "SELECT training_type_id FROM sts_training_type WHERE organization_id = ? AND deleted = 0 LIMIT 1",
      Long.class, organizationId);

    TrainingPlan plan = new TrainingPlan();
    plan.setOrganizationId(organizationId);
    plan.setPlanCode("IT-PLAN-" + UUID.randomUUID());
    plan.setPlanName("阶段替换集成测试计划");
    plan.setTrainingTypeId(trainingTypeId);
    plan.setTrainingNature("INITIAL");
    plan.setSourceOwnerOrganizationId(organizationId);
    plan.setStartTime(LocalDateTime.now());
    plan.setEndTime(LocalDateTime.now().plusDays(1));
    plan.setPublishStatus("DRAFT");
    plan.setStatus(1);
    planMapper.insert(plan);

    TrainingPlanStage existingStage = stage(plan.getTrainingPlanId(), "线上学习阶段");
    stageMapper.insert(existingStage);
    TrainingPlanContent existingContent = content(existingStage.getPlanStageId(), "旧内容");
    contentMapper.insert(existingContent);

    TrainingPlanStageRequest submittedStage = new TrainingPlanStageRequest();
    submittedStage.setStageName("线上学习阶段");
    submittedStage.setStageType("ONLINE");
    submittedStage.setSortOrder(1);
    TrainingPlanContentRequest submittedContent = new TrainingPlanContentRequest();
    submittedContent.setContentType("COURSE");
    submittedContent.setContentName("新内容");
    submittedContent.setIsRequired(1);
    submittedContent.setSortOrder(1);
    submittedStage.setContents(List.of(submittedContent));
    TrainingPlanStagesReplaceRequest request = new TrainingPlanStagesReplaceRequest();
    request.setVersion(0);
    request.setStages(List.of(submittedStage));

    planService.replaceStages(plan.getTrainingPlanId(), request);

    List<TrainingPlanStage> activeStages = stageMapper.selectList(
      new LambdaQueryWrapper<TrainingPlanStage>()
        .eq(TrainingPlanStage::getTrainingPlanId, plan.getTrainingPlanId()));
    assertThat(activeStages).singleElement()
      .satisfies(active -> assertThat(active.getPlanStageId()).isNotEqualTo(existingStage.getPlanStageId()));
    List<TrainingPlanContent> activeContents = contentMapper.selectList(
      new LambdaQueryWrapper<TrainingPlanContent>()
        .eq(TrainingPlanContent::getPlanStageId, activeStages.getFirst().getPlanStageId()));
    assertThat(activeContents).singleElement()
      .satisfies(active -> assertThat(active.getContentName()).isEqualTo("新内容"));
    Long stageDeleted = jdbcTemplate.queryForObject(
      "SELECT deleted FROM sts_training_plan_stage WHERE plan_stage_id = ?",
      Long.class, existingStage.getPlanStageId());
    assertThat(stageDeleted).isEqualTo(existingStage.getPlanStageId());
    Long contentDeleted = jdbcTemplate.queryForObject(
      "SELECT deleted FROM sts_training_plan_content WHERE plan_content_id = ?",
      Long.class, existingContent.getPlanContentId());
    assertThat(contentDeleted).isEqualTo(existingContent.getPlanContentId());
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  @DisplayName("评价表单绑定：保存可读回，同用途重存及删除计划使用主键值软删除")
  void evaluationForms_roundTripAndIdValuedSoftDelete() {
    TrainingPlan plan = insertDraftPlan("评价表单绑定闭环计划");
    assertThat(planService.get(plan.getTrainingPlanId()).getEvaluationForms()).isEmpty();
    EvaluationFormVersion firstVersion = insertPublishedFormVersion("IT-EVAL-FIRST", "首次评价表");
    EvaluationFormVersion secondVersion = insertPublishedFormVersion("IT-EVAL-SECOND", "替换评价表");

    planService.bindEvaluationForms(plan.getTrainingPlanId(), bindRequest(0, firstVersion.getFormVersionId()));
    var firstRead = planService.get(plan.getTrainingPlanId()).getEvaluationForms();
    assertThat(firstRead).singleElement().satisfies(binding -> {
      assertThat(binding.getFormVersionId()).isEqualTo(firstVersion.getFormVersionId());
      assertThat(binding.getEvaluationFormId()).isEqualTo(firstVersion.getEvaluationFormId());
      assertThat(binding.getFormName()).isEqualTo("首次评价表");
      assertThat(binding.getVersionNo()).isEqualTo(1);
      assertThat(binding.getPublishedStatus()).isEqualTo("PUBLISHED");
      assertThat(binding.getUsageType()).isEqualTo("COMPOSITE");
      assertThat(binding.getIsRequired()).isEqualTo(1);
      assertThat(binding.getStatus()).isEqualTo(1);
    });
    Long firstBindingId = firstRead.getFirst().getPlanEvaluationFormId();
    Integer currentVersion = planMapper.selectById(plan.getTrainingPlanId()).getVersion();

    planService.bindEvaluationForms(
      plan.getTrainingPlanId(), bindRequest(currentVersion, secondVersion.getFormVersionId()));

    assertThat(planService.get(plan.getTrainingPlanId()).getEvaluationForms())
      .singleElement()
      .satisfies(binding -> {
        assertThat(binding.getPlanEvaluationFormId()).isNotEqualTo(firstBindingId);
        assertThat(binding.getFormVersionId()).isEqualTo(secondVersion.getFormVersionId());
        assertThat(binding.getFormName()).isEqualTo("替换评价表");
      });
    assertThat(rawBindingDeleted(firstBindingId))
      .isEqualTo(firstBindingId);
    assertThat(planEvaluationFormMapper.selectCount(new LambdaQueryWrapper<com.sts.training.entity.TrainingPlanEvaluationForm>()
                                                      .eq(com.sts.training.entity.TrainingPlanEvaluationForm::getTrainingPlanId,
                                                          plan.getTrainingPlanId())))
      .isEqualTo(1L);

    Integer versionBeforeInvalidBind = planMapper.selectById(plan.getTrainingPlanId()).getVersion();
    EvaluationFormVersion draftVersion = insertFormVersion("IT-EVAL-DRAFT", "未发布评价表", "DRAFT");
    assertThatThrownBy(() -> planService.bindEvaluationForms(
      plan.getTrainingPlanId(), bindRequest(versionBeforeInvalidBind, draftVersion.getFormVersionId())))
      .isInstanceOf(BusinessException.class)
      .hasFieldOrPropertyWithValue("code", 8110);
    assertThat(planMapper.selectById(plan.getTrainingPlanId()).getVersion()).isEqualTo(versionBeforeInvalidBind);
    assertThat(planService.get(plan.getTrainingPlanId()).getEvaluationForms())
      .singleElement()
      .satisfies(binding -> assertThat(binding.getFormVersionId()).isEqualTo(secondVersion.getFormVersionId()));

    Long activeBindingId = planService.get(plan.getTrainingPlanId()).getEvaluationForms()
      .getFirst().getPlanEvaluationFormId();
    planService.delete(plan.getTrainingPlanId());
    assertThat(rawBindingDeleted(activeBindingId))
      .isEqualTo(activeBindingId);
  }

  private TrainingPlan insertDraftPlan(String name) {
    Long organizationId = demoTenantId();
    Long trainingTypeId = jdbcTemplate.queryForObject(
      "SELECT training_type_id FROM sts_training_type WHERE organization_id = ? AND deleted = 0 LIMIT 1",
      Long.class, organizationId);
    TrainingPlan plan = new TrainingPlan();
    plan.setOrganizationId(organizationId);
    plan.setPlanCode("IT-PLAN-" + UUID.randomUUID());
    plan.setPlanName(name);
    plan.setTrainingTypeId(trainingTypeId);
    plan.setTrainingNature("INITIAL");
    plan.setSourceOwnerOrganizationId(organizationId);
    plan.setStartTime(LocalDateTime.now());
    plan.setEndTime(LocalDateTime.now().plusDays(1));
    plan.setPublishStatus("DRAFT");
    plan.setStatus(1);
    planMapper.insert(plan);
    return plan;
  }

  private EvaluationFormVersion insertPublishedFormVersion(String code, String name) {
    return insertFormVersion(code, name, "PUBLISHED");
  }

  private EvaluationFormVersion insertFormVersion(String code, String name, String publishedStatus) {
    EvaluationForm form = new EvaluationForm();
    form.setOrganizationId(demoTenantId());
    form.setFormCode(code + "-" + UUID.randomUUID());
    form.setFormName(name);
    form.setFormType("COMPOSITE");
    form.setCurrentVersionNo(1);
    form.setStatus(1);
    form.setDescription("集成测试评价表");
    evaluationFormMapper.insert(form);
    EvaluationFormVersion version = new EvaluationFormVersion();
    version.setEvaluationFormId(form.getEvaluationFormId());
    version.setVersionNo(1);
    version.setTotalScore(new BigDecimal("100"));
    version.setPublishedStatus(publishedStatus);
    if ("PUBLISHED".equals(publishedStatus)) {
      version.setPublishedBy(1L);
      version.setPublishedTime(LocalDateTime.now());
    }
    version.setVersionNote("集成测试评价表版本");
    evaluationFormVersionMapper.insert(version);
    return version;
  }

  private TrainingPlanEvaluationFormBindRequest bindRequest(Integer version, Long formVersionId) {
    TrainingPlanEvaluationFormItemRequest item = new TrainingPlanEvaluationFormItemRequest();
    item.setFormVersionId(formVersionId);
    item.setUsageType("COMPOSITE");
    item.setIsRequired(1);
    item.setStatus(1);
    TrainingPlanEvaluationFormBindRequest request = new TrainingPlanEvaluationFormBindRequest();
    request.setVersion(version);
    request.setForms(List.of(item));
    return request;
  }

  private Long rawBindingDeleted(Long id) {
    return jdbcTemplate.queryForObject(
      "SELECT deleted FROM sts_training_plan_evaluation_form WHERE plan_evaluation_form_id = ?",
      Long.class, id);
  }

  private TrainingPlanStage stage(Long planId, String name) {
    TrainingPlanStage stage = new TrainingPlanStage();
    stage.setTrainingPlanId(planId);
    stage.setStageName(name);
    stage.setStageType("ONLINE");
    stage.setSortOrder(1);
    stage.setStageStatus("PENDING");
    return stage;
  }

  private TrainingPlanContent content(Long stageId, String name) {
    TrainingPlanContent content = new TrainingPlanContent();
    content.setPlanStageId(stageId);
    content.setContentType("COURSE");
    content.setContentName(name);
    content.setIsRequired(1);
    content.setIsScored(0);
    content.setSortOrder(1);
    content.setContentStatus("PENDING");
    return content;
  }
}

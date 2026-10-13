package com.sts.training.service;

import com.sts.admin.support.AbstractMysqlIntegrationTest;
import com.sts.evaluation.entity.EvaluationForm;
import com.sts.evaluation.entity.EvaluationFormVersion;
import com.sts.evaluation.entity.EvaluationScoreRule;
import com.sts.evaluation.mapper.EvaluationFormMapper;
import com.sts.evaluation.mapper.EvaluationFormVersionMapper;
import com.sts.evaluation.mapper.EvaluationScoreRuleMapper;
import com.sts.org.mapper.OrganizationMapper;
import com.sts.training.entity.TrainingParticipant;
import com.sts.training.entity.TrainingPlan;
import com.sts.training.entity.TrainingPlanEvaluationForm;
import com.sts.training.entity.TrainingRewardPenalty;
import com.sts.training.entity.TrainingType;
import com.sts.training.mapper.TrainingParticipantMapper;
import com.sts.training.mapper.TrainingPlanEvaluationFormMapper;
import com.sts.training.mapper.TrainingPlanMapper;
import com.sts.training.mapper.TrainingRewardPenaltyMapper;
import com.sts.training.mapper.TrainingTypeMapper;
import com.sts.training.support.CompositeScoreItConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = CompositeScoreItConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class CompositeScoreServiceIT extends AbstractMysqlIntegrationTest {

  @Autowired
  private CompositeScoreService compositeScoreService;
  @Autowired
  private OrganizationMapper organizationMapper;
  @Autowired
  private TrainingTypeMapper trainingTypeMapper;
  @Autowired
  private TrainingPlanMapper trainingPlanMapper;
  @Autowired
  private TrainingParticipantMapper trainingParticipantMapper;
  @Autowired
  private TrainingPlanEvaluationFormMapper bindingMapper;
  @Autowired
  private TrainingRewardPenaltyMapper rewardPenaltyMapper;
  @Autowired
  private EvaluationFormMapper formMapper;
  @Autowired
  private EvaluationFormVersionMapper formVersionMapper;
  @Autowired
  private EvaluationScoreRuleMapper scoreRuleMapper;

  @Test
  @DisplayName("考试分与奖惩落库后刷新写出综合分")
  void refresh_persistsWeightedExamPlusReward() {
    TrainingParticipant participant = seedParticipant("IT-COMP-OK");
    bindComposite(participant.getTrainingPlanId(), "EXAM", new BigDecimal("1.00"));
    participant.setExamScore(new BigDecimal("80"));
    TrainingRewardPenalty reward = new TrainingRewardPenalty();
    reward.setTrainingPlanId(participant.getTrainingPlanId());
    reward.setTrainingParticipantId(participant.getTrainingParticipantId());
    reward.setRecordType("REWARD");
    reward.setTitle("加分");
    reward.setDescription("IT");
    reward.setScoreChange(new BigDecimal("5"));
    reward.setOccurredDate(LocalDate.now());
    reward.setStatus(1);
    rewardPenaltyMapper.insert(reward);

    compositeScoreService.refresh(participant);
    trainingParticipantMapper.updateById(participant);

    TrainingParticipant loaded = trainingParticipantMapper.selectById(participant.getTrainingParticipantId());
    assertThat(loaded.getCompositeScore()).isEqualByComparingTo("85.00");
    assertThat(loaded.getResultStatus()).isNotEqualTo("INCOMPLETE");
  }

  @Test
  @DisplayName("缺少考试成绩时综合分为空且结果待补全，不补零")
  void refresh_missingExam_persistsIncomplete() {
    TrainingParticipant participant = seedParticipant("IT-COMP-MISS");
    bindComposite(participant.getTrainingPlanId(), "EXAM", new BigDecimal("1.00"));
    participant.setExamScore(null);
    participant.setResultStatus("LEARNING");

    compositeScoreService.refresh(participant);
    trainingParticipantMapper.updateById(participant);

    TrainingParticipant loaded = trainingParticipantMapper.selectById(participant.getTrainingParticipantId());
    assertThat(loaded.getCompositeScore()).isNull();
    assertThat(loaded.getResultStatus()).isEqualTo("INCOMPLETE");
  }

  private TrainingParticipant seedParticipant(String code) {
    Long orgId = organizationMapper.selectByOrgCode("DEMO").getOrganizationId();
    TrainingType type = new TrainingType();
    type.setOrganizationId(orgId);
    type.setTypeCode(code + "-TYPE");
    type.setTypeName("综合分IT");
    type.setStatus(1);
    type.setDescription("IT");
    trainingTypeMapper.insert(type);
    TrainingPlan plan = new TrainingPlan();
    plan.setOrganizationId(orgId);
    plan.setPlanCode(code);
    plan.setPlanName(code);
    plan.setTrainingTypeId(type.getTrainingTypeId());
    plan.setTrainingNature("INITIAL");
    plan.setStartTime(LocalDateTime.now());
    plan.setEndTime(LocalDateTime.now().plusDays(1));
    plan.setPublishStatus("RUNNING");
    plan.setStatus(1);
    trainingPlanMapper.insert(plan);
    TrainingParticipant participant = new TrainingParticipant();
    participant.setOrganizationId(orgId);
    participant.setTrainingPlanId(plan.getTrainingPlanId());
    participant.setUserId(8800L + Math.abs(code.hashCode() % 1000));
    participant.setParticipantOrgId(orgId);
    participant.setJoinSource("ASSIGN");
    participant.setJoinedTime(LocalDateTime.now());
    participant.setParticipantStatus("LEARNING");
    participant.setOnlineHours(BigDecimal.ZERO);
    participant.setOfflineHours(BigDecimal.ZERO);
    trainingParticipantMapper.insert(participant);
    return participant;
  }

  private void bindComposite(Long planId, String sourceType, BigDecimal weight) {
    EvaluationForm form = new EvaluationForm();
    form.setFormCode("IT-FORM-" + planId);
    form.setFormName("综合评分IT");
    form.setFormType("COMPOSITE");
    form.setCurrentVersionNo(1);
    form.setStatus(1);
    form.setDescription("IT");
    formMapper.insert(form);
    EvaluationFormVersion version = new EvaluationFormVersion();
    version.setEvaluationFormId(form.getEvaluationFormId());
    version.setVersionNo(1);
    version.setTotalScore(new BigDecimal("100"));
    version.setPublishedStatus("PUBLISHED");
    formVersionMapper.insert(version);
    EvaluationScoreRule rule = new EvaluationScoreRule();
    rule.setFormVersionId(version.getFormVersionId());
    rule.setCalculationType("WEIGHT");
    rule.setSourceType(sourceType);
    rule.setWeight(weight);
    rule.setSortOrder(1);
    rule.setRuleDescription("考试权重");
    scoreRuleMapper.insert(rule);
    TrainingPlanEvaluationForm binding = new TrainingPlanEvaluationForm();
    binding.setTrainingPlanId(planId);
    binding.setFormVersionId(version.getFormVersionId());
    binding.setUsageType("COMPOSITE");
    binding.setIsRequired(1);
    binding.setStatus(1);
    bindingMapper.insert(binding);
  }
}

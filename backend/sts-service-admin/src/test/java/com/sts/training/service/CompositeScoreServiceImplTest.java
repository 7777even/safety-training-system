package com.sts.training.service;

import com.sts.evaluation.entity.EvaluationForm;
import com.sts.evaluation.entity.EvaluationFormVersion;
import com.sts.evaluation.entity.EvaluationScoreRule;
import com.sts.evaluation.mapper.EvaluationFormMapper;
import com.sts.evaluation.mapper.EvaluationFormVersionMapper;
import com.sts.evaluation.mapper.EvaluationScoreRuleMapper;
import com.sts.training.entity.TrainingParticipant;
import com.sts.training.entity.TrainingPlanEvaluationForm;
import com.sts.training.entity.TrainingRewardPenalty;
import com.sts.training.mapper.TrainingPlanEvaluationFormMapper;
import com.sts.training.mapper.TrainingRewardPenaltyMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CompositeScoreServiceImplTest {

  private TrainingPlanEvaluationFormMapper bindingMapper;
  private EvaluationFormVersionMapper formVersionMapper;
  private EvaluationFormMapper formMapper;
  private EvaluationScoreRuleMapper scoreRuleMapper;
  private TrainingRewardPenaltyMapper rewardPenaltyMapper;
  private CompositeScoreServiceImpl service;

  @BeforeEach
  void setUp() {
    bindingMapper = mock(TrainingPlanEvaluationFormMapper.class);
    formVersionMapper = mock(EvaluationFormVersionMapper.class);
    formMapper = mock(EvaluationFormMapper.class);
    scoreRuleMapper = mock(EvaluationScoreRuleMapper.class);
    rewardPenaltyMapper = mock(TrainingRewardPenaltyMapper.class);
    service = new CompositeScoreServiceImpl(bindingMapper, formVersionMapper, formMapper, scoreRuleMapper,
                                            rewardPenaltyMapper);
  }

  @Test
  @DisplayName("考试成绩与奖惩变化后自动得到综合分")
  void refresh_computesWeightedExamPlusReward() {
    bindComposite();
    EvaluationScoreRule exam = new EvaluationScoreRule();
    exam.setCalculationType("WEIGHT");
    exam.setSourceType("EXAM");
    exam.setWeight(new BigDecimal("1.00"));
    when(scoreRuleMapper.selectList(any())).thenReturn(List.of(exam));
    TrainingRewardPenalty reward = new TrainingRewardPenalty();
    reward.setScoreChange(new BigDecimal("5"));
    reward.setStatus(1);
    when(rewardPenaltyMapper.selectList(any())).thenReturn(List.of(reward));

    TrainingParticipant participant = participant();
    participant.setExamScore(new BigDecimal("80"));
    service.refresh(participant);
    assertThat(participant.getCompositeScore()).isEqualByComparingTo("85.00");
    assertThat(participant.getResultStatus()).isNotEqualTo("INCOMPLETE");
  }

  @Test
  @DisplayName("缺少规则所需考试成绩时结果待补全且不补零")
  void refresh_missingExam_marksIncomplete() {
    bindComposite();
    EvaluationScoreRule exam = new EvaluationScoreRule();
    exam.setCalculationType("WEIGHT");
    exam.setSourceType("EXAM");
    exam.setWeight(new BigDecimal("1.00"));
    when(scoreRuleMapper.selectList(any())).thenReturn(List.of(exam));

    TrainingParticipant participant = participant();
    participant.setExamScore(null);
    participant.setResultStatus("LEARNING");
    service.refresh(participant);
    assertThat(participant.getCompositeScore()).isNull();
    assertThat(participant.getResultStatus()).isEqualTo("INCOMPLETE");
  }

  private void bindComposite() {
    TrainingPlanEvaluationForm binding = new TrainingPlanEvaluationForm();
    binding.setFormVersionId(8L);
    when(bindingMapper.selectOne(any())).thenReturn(binding);
    EvaluationFormVersion version = new EvaluationFormVersion();
    version.setFormVersionId(8L);
    version.setEvaluationFormId(3L);
    when(formVersionMapper.selectById(8L)).thenReturn(version);
    EvaluationForm form = new EvaluationForm();
    form.setFormType("COMPOSITE");
    form.setStatus(1);
    when(formMapper.selectById(3L)).thenReturn(form);
  }

  private TrainingParticipant participant() {
    TrainingParticipant participant = new TrainingParticipant();
    participant.setTrainingParticipantId(2L);
    participant.setTrainingPlanId(1L);
    return participant;
  }
}

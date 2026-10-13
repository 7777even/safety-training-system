package com.sts.training.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CompositeScoreServiceImpl implements CompositeScoreService {

  private final TrainingPlanEvaluationFormMapper bindingMapper;
  private final EvaluationFormVersionMapper formVersionMapper;
  private final EvaluationFormMapper formMapper;
  private final EvaluationScoreRuleMapper scoreRuleMapper;
  private final TrainingRewardPenaltyMapper rewardPenaltyMapper;

  @Override
  public void refresh(TrainingParticipant participant) {
    if (participant == null || participant.getTrainingPlanId() == null) {
      return;
    }
    TrainingPlanEvaluationForm binding = bindingMapper.selectOne(new LambdaQueryWrapper<TrainingPlanEvaluationForm>()
                                                                   .eq(TrainingPlanEvaluationForm::getTrainingPlanId,
                                                                       participant.getTrainingPlanId())
                                                                   .eq(TrainingPlanEvaluationForm::getUsageType,
                                                                       "COMPOSITE")
                                                                   .eq(TrainingPlanEvaluationForm::getStatus, 1)
                                                                   .last("LIMIT 1"));
    if (binding == null) {
      return;
    }
    EvaluationFormVersion version = formVersionMapper.selectById(binding.getFormVersionId());
    EvaluationForm form = version == null ? null : formMapper.selectById(version.getEvaluationFormId());
    if (version == null || form == null || !"COMPOSITE".equals(form.getFormType()) || !Integer.valueOf(1).equals(form.getStatus())) {
      return;
    }
    List<EvaluationScoreRule> rules = scoreRuleMapper.selectList(
      new LambdaQueryWrapper<EvaluationScoreRule>().eq(EvaluationScoreRule::getFormVersionId,
                                                       version.getFormVersionId()));
    if (rules.isEmpty()) {
      markIncomplete(participant);
      return;
    }
    BigDecimal total = BigDecimal.ZERO;
    for (EvaluationScoreRule rule : rules) {
      if (!"WEIGHT".equals(rule.getCalculationType()) || rule.getWeight() == null) {
        continue;
      }
      BigDecimal sourceScore = sourceScore(participant, rule.getSourceType());
      if (sourceScore == null) {
        markIncomplete(participant);
        return;
      }
      total = total.add(sourceScore.multiply(rule.getWeight()));
    }
    BigDecimal reward = rewardTotal(participant.getTrainingParticipantId());
    participant.setCompositeScore(total.add(reward).setScale(2, RoundingMode.HALF_UP));
  }

  private BigDecimal sourceScore(TrainingParticipant participant, String sourceType) {
    if ("EXAM".equals(sourceType)) {
      return participant.getExamScore();
    }
    if ("COURSE".equals(sourceType)) {
      return participant.getCourseScore();
    }
    if ("OFFLINE".equals(sourceType)) {
      return participant.getOfflineScore();
    }
    return null;
  }

  private BigDecimal rewardTotal(Long trainingParticipantId) {
    if (trainingParticipantId == null) {
      return BigDecimal.ZERO;
    }
    List<TrainingRewardPenalty> records = rewardPenaltyMapper.selectList(
      new LambdaQueryWrapper<TrainingRewardPenalty>()
        .eq(TrainingRewardPenalty::getTrainingParticipantId, trainingParticipantId)
        .eq(TrainingRewardPenalty::getStatus, 1));
    BigDecimal total = BigDecimal.ZERO;
    for (TrainingRewardPenalty record : records) {
      if (record.getScoreChange() != null) {
        total = total.add(record.getScoreChange());
      }
    }
    return total;
  }

  private void markIncomplete(TrainingParticipant participant) {
    participant.setCompositeScore(null);
    participant.setResultStatus("INCOMPLETE");
  }
}

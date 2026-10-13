package com.sts.training.service;

import com.sts.common.api.PageRequest;
import com.sts.common.api.PageResult;
import com.sts.training.dto.*;
import com.sts.training.vo.TrainingGenerateExecutionsResultVO;
import com.sts.training.vo.TrainingParticipantVO;
import com.sts.training.vo.TrainingPlanVO;
import com.sts.training.vo.TrainingPublishPrecheckVO;

import java.util.List;

public interface TrainingPlanService {
  PageResult<TrainingPlanVO> page(PageRequest<TrainingPlanQuery> request);
  TrainingPlanVO get(Long trainingPlanId);
  Long create(TrainingPlanCreateRequest request);
  void update(Long trainingPlanId, TrainingPlanUpdateRequest request);
  void delete(Long trainingPlanId);
  void replaceStages(Long trainingPlanId, TrainingPlanStagesReplaceRequest request);

  void applyTemplate(Long trainingPlanId, TrainingPlanApplyTemplateRequest request);
  void bindEvaluationForms(Long trainingPlanId, TrainingPlanEvaluationFormBindRequest request);

  void publish(Long trainingPlanId, Integer version);

  TrainingPublishPrecheckVO precheck(Long trainingPlanId);
  List<TrainingParticipantVO> listParticipants(Long trainingPlanId);
  TrainingParticipantVO assignParticipant(Long trainingPlanId, TrainingParticipantAssignRequest request);

  List<TrainingParticipantVO> bindSelection(Long trainingPlanId, BindTrainingSelectionRequest request);
  TrainingGenerateExecutionsResultVO generateExecutions(Long trainingPlanId);

  void start(Long trainingPlanId, Integer version);

  void end(Long trainingPlanId, Integer version);

  void cancel(Long trainingPlanId, Integer version);
}

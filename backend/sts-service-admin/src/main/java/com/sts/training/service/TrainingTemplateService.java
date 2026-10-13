package com.sts.training.service;

import com.sts.common.api.PageRequest;
import com.sts.common.api.PageResult;
import com.sts.training.dto.*;
import com.sts.training.vo.TrainingTemplateVO;

public interface TrainingTemplateService {
  PageResult<TrainingTemplateVO> page(PageRequest<TrainingTemplateQuery> request);
  TrainingTemplateVO get(Long trainingTemplateId);
  Long create(TrainingTemplateCreateRequest request);
  void update(Long trainingTemplateId, TrainingTemplateUpdateRequest request);
  void delete(Long trainingTemplateId);
  void replaceStages(Long trainingTemplateId, TrainingTemplateStagesReplaceRequest request);

  void publish(Long trainingTemplateId, Integer version);
}

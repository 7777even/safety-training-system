package com.sts.training.service;

import com.sts.common.api.PageRequest;
import com.sts.common.api.PageResult;
import com.sts.training.dto.*;
import com.sts.training.vo.TrainingStandardVO;

public interface TrainingStandardService {
  PageResult<TrainingStandardVO> page(PageRequest<TrainingStandardQuery> request);
  TrainingStandardVO get(Long trainingStandardId);
  Long create(TrainingStandardCreateRequest request);
  void update(Long trainingStandardId, TrainingStandardUpdateRequest request);
  void delete(Long trainingStandardId);
  void replaceRules(Long trainingStandardId, TrainingStandardRulesReplaceRequest request);

  void publish(Long trainingStandardId, Integer version);
}

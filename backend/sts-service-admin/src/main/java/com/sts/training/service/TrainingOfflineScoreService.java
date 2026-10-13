package com.sts.training.service;

import com.sts.common.api.PageRequest;
import com.sts.common.api.PageResult;
import com.sts.training.dto.TrainingOfflineScoreQuery;
import com.sts.training.dto.TrainingOfflineScoreUpsertRequest;
import com.sts.training.vo.TrainingOfflineScoreVO;

public interface TrainingOfflineScoreService {
  PageResult<TrainingOfflineScoreVO> page(PageRequest<TrainingOfflineScoreQuery> request);

  TrainingOfflineScoreVO upsert(TrainingOfflineScoreUpsertRequest request, Long scoredBy, boolean adminActor);
}

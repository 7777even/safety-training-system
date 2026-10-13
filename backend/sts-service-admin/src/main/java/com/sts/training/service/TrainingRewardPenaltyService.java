package com.sts.training.service;

import com.sts.common.api.PageRequest;
import com.sts.common.api.PageResult;
import com.sts.training.dto.*;
import com.sts.training.vo.TrainingRewardPenaltyVO;

public interface TrainingRewardPenaltyService {
  PageResult<TrainingRewardPenaltyVO> page(PageRequest<TrainingRewardPenaltyQuery> request);
  TrainingRewardPenaltyVO get(Long rewardPenaltyId);
  TrainingRewardEvidenceFile openEvidence(Long rewardPenaltyId);
  Long create(TrainingRewardPenaltyCreateRequest request);
  void update(Long rewardPenaltyId, TrainingRewardPenaltyUpdateRequest request);
  void delete(Long rewardPenaltyId);
}

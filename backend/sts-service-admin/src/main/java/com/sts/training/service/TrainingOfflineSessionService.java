package com.sts.training.service;

import com.sts.common.api.PageRequest;
import com.sts.common.api.PageResult;
import com.sts.training.dto.TrainingOfflineSessionCreateRequest;
import com.sts.training.dto.TrainingOfflineSessionQuery;
import com.sts.training.dto.TrainingOfflineSessionUpdateRequest;
import com.sts.training.vo.TrainingOfflineSessionVO;

public interface TrainingOfflineSessionService {
  PageResult<TrainingOfflineSessionVO> page(PageRequest<TrainingOfflineSessionQuery> request);
  TrainingOfflineSessionVO get(Long offlineSessionId);
  Long create(TrainingOfflineSessionCreateRequest request);
  void update(Long offlineSessionId, TrainingOfflineSessionUpdateRequest request);
  void delete(Long offlineSessionId);

  void start(Long offlineSessionId, Integer version, Long actorUserId, boolean adminActor);

  void complete(Long offlineSessionId, Integer version, Long actorUserId, boolean adminActor);

  void cancel(Long offlineSessionId, Integer version, Long actorUserId, boolean adminActor);
}

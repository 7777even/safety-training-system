package com.sts.training.service;

import com.sts.training.event.TrainingParticipantContentFactChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 同步消费考试、课程写路径发布的培训投影刷新事件，加入当前事务。
 */
@Component
@RequiredArgsConstructor
public class TrainingParticipantContentFactListener {

  private final TrainingResultSyncService resultSyncService;

  @EventListener
  public void onFactChanged(TrainingParticipantContentFactChangedEvent event) {
    resultSyncService.synchronizeParticipantContent(
      event.getTrainingParticipantId(), event.getPlanContentId());
  }
}

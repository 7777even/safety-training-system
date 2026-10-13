package com.sts.training.event;

import org.springframework.context.ApplicationEvent;

/**
 * 培训绑定的来源事实已写入，需在同一事务刷新对应内容投影。
 */
public class TrainingParticipantContentFactChangedEvent extends ApplicationEvent {

  private final Long trainingParticipantId;
  private final Long planContentId;

  public TrainingParticipantContentFactChangedEvent(Object source, Long trainingParticipantId, Long planContentId) {
    super(source);
    this.trainingParticipantId = trainingParticipantId;
    this.planContentId = planContentId;
  }

  public Long getTrainingParticipantId() {
    return trainingParticipantId;
  }

  public Long getPlanContentId() {
    return planContentId;
  }
}

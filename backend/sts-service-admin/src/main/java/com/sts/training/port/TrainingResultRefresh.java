package com.sts.training.port;

/**
 * 培训绑定来源事实变化后，刷新单名参训人的单个内容项投影。
 */
public interface TrainingResultRefresh {

  /**
   * 按参训人与计划内容幂等刷新投影并重建该人聚合字段。空参训人或内容 ID 时忽略。
   */
  void refreshParticipantContent(Long trainingParticipantId, Long planContentId);
}

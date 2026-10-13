package com.sts.training.service;

import com.sts.training.entity.TrainingParticipant;

/**
 * 按培训绑定的综合评分表自动刷新参训人当前综合分。
 */
public interface CompositeScoreService {

  /**
   * 根据考试/课程/线下成绩和奖惩刷新综合分。缺少规则所需成绩时分数为空并将结果标为不完整，不用 0 分代替。
   */
  void refresh(TrainingParticipant participant);
}

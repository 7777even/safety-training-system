package com.sts.training.service;

import com.sts.training.vo.TrainingExecutionLinkVO;
import com.sts.training.vo.TrainingGenerateExecutionsResultVO;

/**
 * 培训执行生成协调器：按内容调用课程/考试 Port 与线下场次，不直接写课程/考试 Mapper。
 */
public interface TrainingExecutionService {

  /**
   * 计划级逐内容生成。各内容独立事务；单内容失败不回滚其他成功内容。
   */
  TrainingGenerateExecutionsResultVO generateExecutions(Long trainingPlanId);

  /**
   * 幂等补建单个计划内容的真实执行实例。
   */
  TrainingExecutionLinkVO provision(Long trainingPlanId, Long planContentId);
}

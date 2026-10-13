package com.sts.training.service;

/**
 * 培训计划 RUNNING / ENDED / CANCELLED 生命周期。
 */
public interface TrainingPlanLifecycleService {

  void start(Long trainingPlanId, Integer expectedVersion);

  void end(Long trainingPlanId, Integer expectedVersion);

  void cancel(Long trainingPlanId, Integer expectedVersion);
}

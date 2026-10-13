package com.sts.training.vo;

import lombok.Data;

import java.util.List;

@Data
public class TrainingLearnerParticipantDetailVO {
  /**
   * 当前学员的参训记录。
   */
  private TrainingParticipantVO participant;
  /** 所属培训计划，含必返 publishStatus；学员详情不装配 stages。 */
  private TrainingPlanVO plan;
  private List<TrainingPlanContentVO> contents;
  private List<TrainingParticipantResultItemVO> results;
}

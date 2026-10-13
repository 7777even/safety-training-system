package com.sts.training.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 培训计划发布预检结果。ready=true 表示当前可以发布。
 */
@Data
public class TrainingPublishPrecheckVO {
  private Long trainingPlanId;
  private Boolean ready;
  private List<TrainingPublishIssueVO> issues = new ArrayList<>();
}

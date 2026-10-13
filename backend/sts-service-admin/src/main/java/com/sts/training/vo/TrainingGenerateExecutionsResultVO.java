package com.sts.training.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class TrainingGenerateExecutionsResultVO {
  private Long trainingPlanId;
  private int offlineSessionsCreated;
  private int executionLinksCreated;
  private int skippedExisting;
  /**
   * 本次失败的内容数量。
   */
  private int failedCount;
  /**
   * 单内容失败明细；已成功内容不受影响。
   */
  private List<TrainingGenerateExecutionFailureVO> failures = new ArrayList<>();
}

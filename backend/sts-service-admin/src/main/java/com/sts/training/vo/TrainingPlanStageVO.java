package com.sts.training.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class TrainingPlanStageVO {
  private Long planStageId;
  private Long trainingPlanId;
  private Long sourceTemplateStageId;
  private String stageName;
  private String stageType;
  private LocalDateTime startTime;
  private LocalDateTime endTime;
  private String location;
  private BigDecimal requiredHours;
  private Integer sortOrder;
  private String stageStatus;
  private List<TrainingPlanContentVO> contents;
}

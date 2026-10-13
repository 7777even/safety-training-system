package com.sts.training.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class TrainingTemplateStageVO {
  private Long templateStageId;
  private Long trainingTemplateId;
  private String stageName;
  private String stageType;
  private BigDecimal requiredHours;
  private Integer sortOrder;
  private Integer status;
  private List<TrainingTemplateContentVO> contents;
}

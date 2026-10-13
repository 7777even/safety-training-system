package com.sts.training.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sts.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sts_training_plan_stage")
public class TrainingPlanStage extends BaseEntity {
  @TableId(type = IdType.AUTO)
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
}

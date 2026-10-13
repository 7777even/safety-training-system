package com.sts.training.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sts.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sts_training_template_stage")
public class TrainingTemplateStage extends BaseEntity {
  @TableId(type = IdType.AUTO)
  private Long templateStageId;
  private Long trainingTemplateId;
  private String stageName;
  private String stageType;
  private BigDecimal requiredHours;
  private Integer sortOrder;
  private Integer status;
}

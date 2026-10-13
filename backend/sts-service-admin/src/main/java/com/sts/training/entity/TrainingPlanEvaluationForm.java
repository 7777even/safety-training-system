package com.sts.training.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sts.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sts_training_plan_evaluation_form")
public class TrainingPlanEvaluationForm extends BaseEntity {
  @TableId(type = IdType.AUTO)
  private Long planEvaluationFormId;
  private Long trainingPlanId;
  private Long formVersionId;
  private String usageType;
  private Integer isRequired;
  private Integer status;
}

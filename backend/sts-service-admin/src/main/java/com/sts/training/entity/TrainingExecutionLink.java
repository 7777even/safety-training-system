package com.sts.training.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sts.common.entity.BaseAuditEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sts_training_execution_link")
public class TrainingExecutionLink extends BaseAuditEntity {
  @TableId(type = IdType.AUTO)
  private Long executionLinkId;
  private Long trainingPlanId;
  private Long planContentId;
  private String executionType;
  private Long executionId;
}

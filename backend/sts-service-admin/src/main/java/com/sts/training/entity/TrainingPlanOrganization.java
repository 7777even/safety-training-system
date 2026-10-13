package com.sts.training.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sts.common.entity.BaseAuditEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sts_training_plan_organization")
public class TrainingPlanOrganization extends BaseAuditEntity {
  @TableId(type = IdType.AUTO)
  private Long planOrganizationId;
  private Long trainingPlanId;
  private Long participantOrganizationId;
  private String organizationRole;
}

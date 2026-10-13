package com.sts.training.entity;
import com.baomidou.mybatisplus.annotation.*;
import com.sts.common.entity.BaseEntity;
import lombok.*;
/** 每次提交保留独立审批记录，不覆盖历史。 */
@Data @EqualsAndHashCode(callSuper=true) @TableName("sts_training_plan_approval")
public class TrainingPlanApproval extends BaseEntity {
  /** 主键。 */ @TableId(type=IdType.AUTO) private Long approvalId;
  /** 机构。 */ private Long organizationId;
  /** 计划。 */ private Long trainingPlanId;
  /** 审批时计划版本。 */ private Integer planVersion;
  /** 提交人。 */ private Long submitterUserId;
  /** 指定审批人。 */ private Long approverUserId;
  /** 审批状态。 */ private String approvalStatus;
  /** 审批说明。 */ private String decisionComment;
}

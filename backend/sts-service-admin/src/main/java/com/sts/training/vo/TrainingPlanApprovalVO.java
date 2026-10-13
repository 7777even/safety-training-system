package com.sts.training.vo;
import lombok.Data;
/** 审批历史。 */
@Data public class TrainingPlanApprovalVO {
  /** 主键。 */ private Long approvalId;
  /** 计划。 */ private Long trainingPlanId;
  /** 审批时计划版本。 */ private Integer planVersion;
  /** 提交人。 */ private Long submitterUserId;
  /** 指定审批人。 */ private Long approverUserId;
  /** 审批状态。 */ private String approvalStatus;
  /** 审批说明。 */ private String decisionComment;
  /** 记录版本。 */ private Integer version;
  /** 创建时间。 */ private java.time.LocalDateTime createTime;
}

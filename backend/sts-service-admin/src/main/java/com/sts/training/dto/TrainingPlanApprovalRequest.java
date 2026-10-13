package com.sts.training.dto;
import jakarta.validation.constraints.*;
import lombok.Data;
/** 指定单级审批人并绑定当前版本。 */
@Data public class TrainingPlanApprovalRequest {
  /** 计划版本。 */ @NotNull @Min(0) private Integer version;
  /** 审批人。 */ @NotNull @Positive private Long approverUserId;
}

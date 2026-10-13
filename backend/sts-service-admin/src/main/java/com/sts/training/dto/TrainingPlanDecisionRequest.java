package com.sts.training.dto;
import jakarta.validation.constraints.*;
import lombok.Data;
/** 审批结论。 */
@Data public class TrainingPlanDecisionRequest {
  /** 是否批准。 */ @NotNull private Boolean approved;
  /** 审批意见，驳回必填。 */ @Size(max=1000) private String comment;
}

package com.sts.training.dto;
import jakarta.validation.constraints.*;
import lombok.Data;
/** 将年度文件冻结关联到计划。 */
@Data public class TrainingAnnualPlanBindRequest {
  /** 年度计划，空值解除关联。 */ @Positive private Long annualPlanId;
  /** 计划版本。 */ @NotNull @Min(0) private Integer version;
}

package com.sts.training.dto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
/** 独立变更草稿，不直接写原计划。 */
@Data public class TrainingPlanChangeRequest {
  /** 创建时生效计划的版本。 */ @NotNull @Min(0) private Integer baseVersion;
  /** 编辑时变更记录版本。 */ @Min(0) private Integer version;
  /** BASIC四项基础信息或FULL完整替换。 */ @NotBlank @Pattern(regexp="BASIC|FULL") private String changeType;
  /** BASIC内容。 */ @Valid private TrainingPlanBasicChangeRequest basic;
  /** FULL内容。 */ @Valid private TrainingPlanFullChangeRequest full;
}

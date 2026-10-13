package com.sts.training.dto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.util.List;
/** 管理员选择矩阵并核对计划基础信息后创建草稿。 */
@Data public class TrainingMatrixPlanRequest {
  /** 已核对的计划基础信息。 */ @NotNull @Valid private TrainingPlanCreateRequest plan;
  /** 选中的岗位培训要求。 */ @NotEmpty @Size(max=200) private List<@NotNull @Positive Long> matrixIds;
}

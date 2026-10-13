package com.sts.training.dto;
import jakarta.validation.constraints.*;
import lombok.Data;
/** 执行中仅可调整的四项基础信息。 */
@Data public class TrainingPlanBasicChangeRequest {
  /** 计划名称。 */ @NotBlank @Size(max=150) private String planName;
  /** 负责人，空值清除。 */ @Positive private Long managerUserId;
  /** 地点。 */ @Size(max=255) private String location;
  /** 说明。 */ @Size(max=10000) private String description;
}

package com.sts.training.dto;
import jakarta.validation.constraints.*;
import lombok.Data;
/** 年度文件维护入参。 */
@Data public class TrainingAnnualPlanRequest {
  /** 年度。 */ @NotNull @Min(1900) @Max(9999) private Integer planYear;
  /** 名称。 */ @NotBlank @Size(max=150) private String planName;
  /** 上传成功的文件。 */ @NotNull @Positive private Long fileInfoId;
  /** 编辑时版本。 */ @Min(0) private Integer version;
}

package com.sts.training.dto;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;
/** 岗位培训要求维护入参。 */
@Data public class TrainingMatrixRequest {
  /** 岗位。 */ @NotNull @Positive private Long positionId;
  /** 培训内容。 */ @NotBlank @Size(max=150) private String contentName;
  /** 课程。 */ @NotNull @Positive private Long courseId;
  /** 已发布课程版本。 */ @NotNull @Positive private Long courseVersionId;
  /** 要求学时。 */ @NotNull @DecimalMin("0.01") @Digits(integer=6,fraction=2) private BigDecimal requiredHours;
  /** 状态。 */ @NotNull @Min(0) @Max(1) private Integer status;
  /** 编辑时必填的乐观锁版本。 */ @Min(0) private Integer version;
}

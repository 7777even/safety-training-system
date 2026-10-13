package com.sts.training.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class TrainingTypeUpdateRequest {
  @NotNull private Integer version;
  @NotBlank private String typeName;
  private String workType;
  private String tag;
  private Long coverFileId;
  @NotNull
  @DecimalMin(value = "0", inclusive = false, message = "初培学时必须大于 0")
  private BigDecimal initialHours;
  @NotNull
  @DecimalMin(value = "0", inclusive = false, message = "复审学时必须大于 0")
  private BigDecimal reviewHours;
  private BigDecimal onlineMaxRatio;
  @NotNull private Integer status;
  @NotBlank private String description;
}

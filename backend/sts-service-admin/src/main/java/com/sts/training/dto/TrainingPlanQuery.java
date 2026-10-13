package com.sts.training.dto;

import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class TrainingPlanQuery {
  private String planCode;
  private String planName;
  private Long trainingTypeId;
  @Pattern(regexp = "INITIAL|REVIEW|UNSPECIFIED", message = "培训性质筛选值不合法")
  private String trainingNature;
  private String publishStatus;
  private Integer status;
}

package com.sts.training.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TrainingTemplateUpdateRequest {
  @NotNull private Integer version;
  @NotBlank private String templateName;
  private Long templateCategoryId;
  @NotNull private Long trainingTypeId;
  private Long trainingStandardId;
  private String description;
  @NotNull private Integer status;
}

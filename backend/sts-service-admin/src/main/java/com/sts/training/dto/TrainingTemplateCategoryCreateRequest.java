package com.sts.training.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TrainingTemplateCategoryCreateRequest {
  private Long parentId = 0L;
  @NotBlank private String categoryName;
  @NotNull private Integer sortOrder;
  @NotNull private Integer status;
}

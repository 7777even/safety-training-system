package com.sts.course.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CategoryUpdateRequest {
  @NotNull(message = "父分类ID不能为空")
  private Long parentId;

  @NotBlank(message = "分类名称不能为空")
  private String categoryName;

  private String description = "";
  private Integer sortOrder;

  @NotNull(message = "状态不能为空")
  private Integer status;

  @NotNull(message = "版本号不能为空")
  private Integer version;
}

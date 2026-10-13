package com.sts.training.dto;

import lombok.Data;

@Data
public class TrainingTemplateCategoryQuery {
  private Long parentId;
  private String categoryName;
  private Integer status;
}

package com.sts.training.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class TrainingTemplateCategoryTreeVO {
  private Long templateCategoryId;
  private Long organizationId;
  private Long parentId;
  private String categoryName;
  private Integer sortOrder;
  private Integer status;
  private List<TrainingTemplateCategoryTreeVO> children = new ArrayList<>();
}

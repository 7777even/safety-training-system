package com.sts.training.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TrainingTemplateCategoryVO {
  private Long templateCategoryId;
  private Long organizationId;
  private Long parentId;
  private String ancestors;
  private String categoryName;
  private Integer sortOrder;
  private Integer status;
  private Integer version;
  private LocalDateTime createTime;
}

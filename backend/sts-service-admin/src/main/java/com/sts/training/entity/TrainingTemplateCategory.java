package com.sts.training.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sts.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sts_training_template_category")
public class TrainingTemplateCategory extends BaseEntity {
  @TableId(type = IdType.AUTO)
  private Long templateCategoryId;
  private Long organizationId;
  private Long parentId;
  private String ancestors;
  private String categoryName;
  private Integer sortOrder;
  private Integer status;
}

package com.sts.training.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sts.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sts_training_template")
public class TrainingTemplate extends BaseEntity {
  @TableId(type = IdType.AUTO)
  private Long trainingTemplateId;
  private Long organizationId;
  private String templateCode;
  private String templateName;
  private Long templateCategoryId;
  private Long trainingTypeId;
  private Long trainingStandardId;
  private String description;
  private String publishStatus;
  private Integer status;
}

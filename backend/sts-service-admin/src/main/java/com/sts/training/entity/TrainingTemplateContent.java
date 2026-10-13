package com.sts.training.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sts.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sts_training_template_content")
public class TrainingTemplateContent extends BaseEntity {
  @TableId(type = IdType.AUTO)
  private Long templateContentId;
  private Long templateStageId;
  private String contentType;
  private Long resourceId;
  private Long resourceVersionId;
  private String assessmentMode;
  private String executionConfigJson;
  private String contentName;
  private Integer isRequired;
  private BigDecimal requiredHours;
  private Integer sortOrder;
  private Integer status;
}

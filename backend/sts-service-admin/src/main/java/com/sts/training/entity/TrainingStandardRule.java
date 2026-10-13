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
@TableName("sts_training_standard_rule")
public class TrainingStandardRule extends BaseEntity {
  @TableId(type = IdType.AUTO)
  private Long standardRuleId;
  private Long trainingStandardId;
  private String ruleType;
  private BigDecimal requiredValue;
  private String unit;
  private String resourceType;
  private Long resourceId;
  private Integer sortOrder;
  private Integer status;
  private String description;
}

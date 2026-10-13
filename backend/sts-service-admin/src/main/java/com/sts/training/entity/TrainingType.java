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
@TableName("sts_training_type")
public class TrainingType extends BaseEntity {
  @TableId(type = IdType.AUTO)
  private Long trainingTypeId;
  private Long organizationId;
  private String typeCode;
  private String typeName;
  private String workType;
  private String tag;
  private Long coverFileId;
  private BigDecimal initialHours;
  private BigDecimal reviewHours;
  private BigDecimal onlineMaxRatio;
  private Integer status;
  private String description;
}

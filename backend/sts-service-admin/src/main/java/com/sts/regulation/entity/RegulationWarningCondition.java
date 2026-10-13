package com.sts.regulation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sts.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 【暂时隐藏】原可配置预警条件；当前中山50最小闭环不读写。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sts_regulation_warning_condition")
public class RegulationWarningCondition extends BaseEntity {
  @TableId(type = IdType.AUTO)
  private Long warningConditionId;
  private Long warningRuleId;
  private String metricCode;
  private String operator;
  private BigDecimal thresholdValue;
  private String unit;
  private Integer sortOrder;
  private Integer status;
}

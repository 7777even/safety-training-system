package com.sts.regulation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sts.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 【内部保留】固定系统学时不足规则；不提供规则管理页面或对外操作。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sts_regulation_warning_rule")
public class RegulationWarningRule extends BaseEntity {
  @TableId(type = IdType.AUTO)
  private Long warningRuleId;
  private Long organizationId;
  private String ruleCode;
  private String ruleName;
  private String warningType;
  private Long trainingTypeId;
  private String warningLevel;
  private LocalDateTime validFrom;
  private LocalDateTime validTo;
  private Integer status;
  private String description;
}

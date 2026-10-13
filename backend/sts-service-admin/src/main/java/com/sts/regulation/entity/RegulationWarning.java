package com.sts.regulation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sts.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 监管预警。当前仅用于培训计划学时不足的待查看/已查看记录。
 * {@code currentAssigneeId} 暂时隐藏；{@code warningLevel} 固定内部值；{@code closedTime} 内部结束使用。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sts_regulation_warning")
public class RegulationWarning extends BaseEntity {
  @TableId(type = IdType.AUTO)
  private Long warningId;
  private Long organizationId;
  private Long warningRuleId;
  private Long regulatedOrgId;
  private Long trainingPlanId;
  private String detectionCycle;
  private String dedupKey;
  private String warningLevel;
  private String warningTitle;
  private String warningStatus;
  private LocalDateTime detectedTime;
  private LocalDateTime firstViewedTime;
  private LocalDateTime closedTime;
  private Long currentAssigneeId;
}

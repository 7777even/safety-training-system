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
@TableName("sts_training_plan_content")
public class TrainingPlanContent extends BaseEntity {
  @TableId(type = IdType.AUTO)
  private Long planContentId;
  private Long planStageId;
  private String contentType;
  private Long resourceId;
  private Long resourceVersionId;
  private String assessmentMode;
  private String executionConfigJson;
  private String resourceSnapshotJson;
  private String resourceSnapshotHash;
  private String contentName;
  private Integer isRequired;
  private Integer isScored;
  private BigDecimal scoreWeight;
  private String scoreAggregationMode;
  /**
   * 安排学时，仅用于发布校验；不是学员获得学时。
   */
  private BigDecimal requiredHours;
  private Integer sortOrder;
  private String contentStatus;
}

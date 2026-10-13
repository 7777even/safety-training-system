package com.sts.training.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sts.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sts_training_plan")
public class TrainingPlan extends BaseEntity {
  @TableId(type = IdType.AUTO)
  private Long trainingPlanId;
  private Long organizationId;
  private String planCode;
  private String planName;
  private Long trainingTypeId;
  private String trainingNature;
  private BigDecimal applicableRequiredHours;
  /** 允许更新请求以 null 清除已绑定的培训标准。 */
  @TableField(updateStrategy = FieldStrategy.ALWAYS)
  private Long trainingStandardId;
  /** 允许更新请求以 null 清除已绑定的培训模板。 */
  @TableField(updateStrategy = FieldStrategy.ALWAYS)
  private Long sourceTemplateId;
  private Long sourceOwnerOrganizationId;
  private String trainingTypeSnapshotJson;
  private String trainingStandardSnapshotJson;
  private String templateSnapshotJson;
  private String sourceSnapshotHash;
  /** 允许批准变更明确清空该基础信息。 */
  @TableField(updateStrategy = FieldStrategy.ALWAYS)
  private Long managerUserId;
  private LocalDateTime startTime;
  private LocalDateTime endTime;
  /** 允许批准变更明确清空该基础信息。 */
  @TableField(updateStrategy = FieldStrategy.ALWAYS)
  private String location;
  /** 允许批准变更明确清空该基础信息。 */
  @TableField(updateStrategy = FieldStrategy.ALWAYS)
  private String description;
  private String publishStatus;
  private LocalDateTime publishedTime;
  private LocalDateTime endedTime;
  private Integer status;
}

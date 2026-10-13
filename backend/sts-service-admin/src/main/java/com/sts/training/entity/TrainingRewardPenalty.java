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
import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sts_training_reward_penalty")
public class TrainingRewardPenalty extends BaseEntity {
  @TableId(type = IdType.AUTO)
  private Long rewardPenaltyId;
  private Long organizationId;
  private Long trainingPlanId;
  private Long trainingParticipantId;
  private String recordType;
  private String title;
  /** 编辑时允许清空说明，避免旧值在数据库中残留。 */
  @TableField(updateStrategy = FieldStrategy.ALWAYS)
  private String description;
  /** 空值表示只记录奖惩事实，不变更综合分。 */
  @TableField(updateStrategy = FieldStrategy.ALWAYS)
  private BigDecimal scoreChange;
  /** 编辑时允许清空发生日期。 */
  @TableField(updateStrategy = FieldStrategy.ALWAYS)
  private LocalDate occurredDate;
  /** 编辑时允许移除佐证文件关联。 */
  @TableField(updateStrategy = FieldStrategy.ALWAYS)
  private Long evidenceFileId;
  private Integer status;
}

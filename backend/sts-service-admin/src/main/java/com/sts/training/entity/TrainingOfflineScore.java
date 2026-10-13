package com.sts.training.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sts.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sts_training_offline_score")
public class TrainingOfflineScore extends BaseEntity {
  @TableId(type = IdType.AUTO)
  private Long offlineScoreId;
  private Long organizationId;
  private Long trainingParticipantId;
  private Long offlineSessionId;
  private String scoreName;
  private BigDecimal score;
  private BigDecimal maxScore;
  private String scoreSource;
  private Long importBatchId;
  private Long scoredBy;
  private LocalDateTime scoredTime;
}

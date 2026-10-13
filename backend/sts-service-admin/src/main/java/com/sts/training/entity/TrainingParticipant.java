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
@TableName("sts_training_participant")
public class TrainingParticipant extends BaseEntity {
  @TableId(type = IdType.AUTO)
  private Long trainingParticipantId;
  private Long organizationId;
  private Long trainingPlanId;
  private Long userId;
  private Long participantOrgId;
  private String joinSource;
  private LocalDateTime joinedTime;
  private String participantStatus;
  /**
   * 获得的线上学时（小时），仅累计课程正式学习秒。考试/题库不计入。
   */
  private BigDecimal onlineHours;
  /**
   * 获得的线下学时（小时），仅累计已评分且合格的线下安排学时；未评分或不合格为 0。
   */
  private BigDecimal offlineHours;
  private BigDecimal courseScore;
  private BigDecimal examScore;
  private BigDecimal offlineScore;
  private BigDecimal compositeScore;
  private Long officialCalculationRunId;
  private String resultStatus;
  private LocalDateTime completedTime;
  private String finalResultSnapshotJson;
  private String finalResultSnapshotHash;
  private LocalDateTime resultFinalizedTime;
}

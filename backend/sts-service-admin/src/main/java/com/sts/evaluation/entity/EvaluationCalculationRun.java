package com.sts.evaluation.entity;

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
@TableName("sts_evaluation_calculation_run")
public class EvaluationCalculationRun extends BaseEntity {
  @TableId(type = IdType.AUTO)
  private Long calculationRunId;
  private Long organizationId;
  private Long trainingPlanId;
  private Long trainingParticipantId;
  private Long formVersionId;
  private Integer runNo;
  private Long supersedesRunId;
  private String calculationStatus;
  private Integer officialMarker;
  private BigDecimal courseScore;
  private BigDecimal examScore;
  private BigDecimal offlineScore;
  private BigDecimal adjustmentScore;
  private BigDecimal compositeScore;
  private String ruleSnapshotJson;
  private String calculationHash;
  private Long calculatedBy;
  private LocalDateTime calculatedTime;
  private Long confirmedBy;
  private LocalDateTime confirmedTime;
}

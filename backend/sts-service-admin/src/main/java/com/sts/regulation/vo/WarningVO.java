package com.sts.regulation.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class WarningVO {
  private Long warningId;
  private Long organizationId;
  private Long warningRuleId;
  private Long regulatedOrgId;
  private String regulatedOrgName;
  private Long trainingPlanId;
  private String planCode;
  private String planName;
  private Long trainingTypeId;
  private String trainingTypeName;
  private String trainingNature;
  private java.math.BigDecimal planTotalHours;
  private java.math.BigDecimal requiredHours;
  private java.math.BigDecimal hoursGap;
  private String detectionCycle;
  private String warningLevel;
  private String warningTitle;
  private String warningStatus;
  private LocalDateTime detectedTime;
  private LocalDateTime firstViewedTime;
  private LocalDateTime closedTime;
  private Integer version;
}

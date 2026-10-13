package com.sts.regulator.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 学习情况统计，园区与单机构共用口径。
 */
@Data
public class LearningStatsVO {
  private Long organizationId;
  private String orgCode;
  private String orgName;
  private String orgType;
  private long employeeAccountCount;
  private long classSessionCount;
  private long classLearnerCount;
  private long participatingOfferingCount;
  private BigDecimal avgSessionsPerLearner;
  private BigDecimal participationRate;
  private List<LearningStatsVO> institutions = new ArrayList<>();
}

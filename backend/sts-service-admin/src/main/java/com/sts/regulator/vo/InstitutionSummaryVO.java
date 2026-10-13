package com.sts.regulator.vo;

import lombok.Data;

@Data
public class InstitutionSummaryVO {
  private Long organizationId;
  private String orgCode;
  private String orgName;
  private String orgType;
  private long planCount;
  private long participantCount;
}

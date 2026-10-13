package com.sts.regulator.vo;

import lombok.Data;

@Data
public class WorkbenchTotalsVO {
  private long enterpriseCount;
  private long trainingInstitutionCount;
  private long planTotalCount;
  private long runningPlanCount;
  private long endedPlanCount;
  private long participantCount;
  private long completedParticipantCount;
}

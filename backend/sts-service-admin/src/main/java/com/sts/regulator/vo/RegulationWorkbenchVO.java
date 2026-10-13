package com.sts.regulator.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 园区监管工作台概览。
 */
@Data
public class RegulationWorkbenchVO {
  private long enterpriseCount;
  private long trainingInstitutionCount;
  private long planTotalCount;
  private long runningPlanCount;
  private long endedPlanCount;
  private long participantCount;
  private long completedParticipantCount;
  private long unreadWarningCount;
  private List<TrainingTypeShareVO> trainingTypeDistribution = new ArrayList<>();
  private SpecialOperationStatsVO specialOperation = new SpecialOperationStatsVO();
  private List<InstitutionSummaryVO> institutions = new ArrayList<>();
  private List<WorkbenchShortcutVO> shortcuts = new ArrayList<>();
}

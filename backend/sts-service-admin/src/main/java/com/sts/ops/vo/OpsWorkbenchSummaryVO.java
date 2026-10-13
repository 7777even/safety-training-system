package com.sts.ops.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 企业管理员工作台：权限化入口、可解释概览与分类待办。
 */
@Data
public class OpsWorkbenchSummaryVO {
  private List<OpsWorkbenchShortcutVO> shortcuts = new ArrayList<>();
  private long publishedCourseCount;
  private long runningExamCount;
  private long runningPlanCount;
  private long currentParticipantCount;
  private long endedPassedCount;
  private long endedFailedCount;
  private long openTodoCount;
  private List<OpsTodoCategoryVO> todos = new ArrayList<>();
}

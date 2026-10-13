package com.sts.exam.dto;

import lombok.Data;

/**
 * 学员本人考试任务筛选条件。
 */
@Data
public class ExamLearnerTaskQuery {
  private String taskStatus;
  private String examName;
  private String sourceType;
}

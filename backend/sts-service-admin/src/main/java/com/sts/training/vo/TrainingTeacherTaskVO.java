package com.sts.training.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TrainingTeacherTaskVO {
  private Long offlineSessionId;
  private Long trainingPlanId;
  /** 所属培训计划的当前名称；计划资料缺失时为空。 */
  private String trainingPlanName;
  private String sessionName;
  private String location;
  private LocalDateTime startTime;
  private LocalDateTime endTime;
  private String sessionStatus;
  private Integer version;
  private long participantCount;
  private long scoredCount;
  private long pendingScoreCount;
}

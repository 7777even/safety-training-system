package com.sts.training.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TrainingOfflineSessionVO {
  private Long offlineSessionId;
  private Long trainingPlanId;
  private Long planStageId;
  private Long planContentId;
  private String sessionName;
  private Long teacherUserId;
  private String location;
  private LocalDateTime startTime;
  private LocalDateTime endTime;
  private String sessionStatus;
  private Integer status;
  private Integer version;
  private LocalDateTime createTime;
}

package com.sts.course.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LearnerLiveTaskReferenceVO {
  private Long liveSessionId;
  private String liveName;
  private Long teacherUserId;
  private String liveUrl;
  private LocalDateTime startTime;
  private LocalDateTime endTime;
  private String liveStatus;
}

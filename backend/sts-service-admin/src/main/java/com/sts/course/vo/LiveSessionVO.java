package com.sts.course.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LiveSessionVO {
  private Long liveSessionId;
  private Long courseItemId;
  private String liveName;
  private Long teacherUserId;
  private String liveUrl;
  private LocalDateTime startTime;
  private LocalDateTime endTime;
  private String liveStatus;
  private Integer status;
  private Integer version;
}

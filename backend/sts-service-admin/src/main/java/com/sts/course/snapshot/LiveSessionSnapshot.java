package com.sts.course.snapshot;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@JsonPropertyOrder({
  "liveSessionId", "liveName", "teacherUserId", "liveUrl", "startTime", "endTime",
  "liveStatus", "status"
})
public class LiveSessionSnapshot {
  private Long liveSessionId;
  private String liveName;
  private Long teacherUserId;
  private String liveUrl;
  private LocalDateTime startTime;
  private LocalDateTime endTime;
  private String liveStatus;
  private Integer status;
}

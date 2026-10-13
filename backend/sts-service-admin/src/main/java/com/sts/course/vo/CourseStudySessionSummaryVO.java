package com.sts.course.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CourseStudySessionSummaryVO {
  private Long studySessionId;
  private Long courseItemId;
  private String deviceType;
  private LocalDateTime startedTime;
  private LocalDateTime endedTime;
  private Integer validStudySeconds;
  private Integer endPositionSeconds;
  private Integer endPageNo;
  private String endReason;
  private String clientTraceId;
}

package com.sts.course.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CourseItemProgressVO {
  private Long itemProgressId;
  private Long courseItemId;
  private String progressStatus;
  private Integer studySeconds;
  private Integer lastPositionSeconds;
  private Integer lastPageNo;
  private Integer attemptCount;
  private LocalDateTime firstStartTime;
  private LocalDateTime completedTime;
  private LocalDateTime lastStudyTime;
}

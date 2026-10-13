package com.sts.course.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class LearnerCourseTasksVO {
  private Long enrollmentId;
  private Long offeringId;
  private Long courseId;
  private String courseName;
  private String offeringName;
  private String courseType;
  private LocalDateTime startTime;
  private LocalDateTime endTime;
  private String learningStatus;
  private BigDecimal progressPercent;
  private String coverUrl;
  private List<LearnerCourseTaskChapterVO> chapters;
}

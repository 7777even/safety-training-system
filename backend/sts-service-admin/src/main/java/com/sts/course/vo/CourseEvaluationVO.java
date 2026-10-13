package com.sts.course.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class CourseEvaluationVO {
  private Long courseEvaluationId;
  private BigDecimal rating;
  private String content;
  private Integer isAnonymous;
  private LocalDateTime submittedTime;
}

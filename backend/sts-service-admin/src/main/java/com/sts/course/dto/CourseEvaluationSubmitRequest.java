package com.sts.course.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CourseEvaluationSubmitRequest {
  @NotNull(message = "评分不能为空")
  @DecimalMin(value = "0.0", message = "评分不能小于0")
  @DecimalMax(value = "5.0", message = "评分不能大于5")
  private BigDecimal rating;
  private String content;
  @NotNull(message = "是否匿名不能为空")
  private Integer isAnonymous;
}

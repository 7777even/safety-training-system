package com.sts.course.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CourseOfferingStatusRequest {
  @NotNull(message = "版本号不能为空")
  private Integer version;
}

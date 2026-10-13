package com.sts.course.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CourseAssessmentStartRequest {
  @NotBlank(message = "开考幂等令牌不能为空")
  private String clientAttemptToken;
  private String platform;
  private String operatingSystem;
  private String browser;
  private String clientIp;
}

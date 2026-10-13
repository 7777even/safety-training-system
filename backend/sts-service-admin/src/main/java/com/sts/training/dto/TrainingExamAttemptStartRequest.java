package com.sts.training.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TrainingExamAttemptStartRequest {
  @NotBlank
  private String clientAttemptToken;
  private String platform;
  private String operatingSystem;
  private String browser;
  private String clientIp;
}

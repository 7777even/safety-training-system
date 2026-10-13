package com.sts.exam.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PracticeResetRequest {
  @NotBlank private String resetScope;
  private String reason;
}

package com.sts.exam.dto;

import lombok.Data;

@Data
public class SubmitAttemptRequest {
  private String submitReason = "MANUAL";
  private Integer usedSeconds;
}

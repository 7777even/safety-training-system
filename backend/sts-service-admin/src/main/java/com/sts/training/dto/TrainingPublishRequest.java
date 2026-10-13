package com.sts.training.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TrainingPublishRequest {
  @NotNull
  private Integer version;
}

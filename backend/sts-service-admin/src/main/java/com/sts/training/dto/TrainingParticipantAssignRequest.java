package com.sts.training.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TrainingParticipantAssignRequest {
  @NotNull private Long userId;
}

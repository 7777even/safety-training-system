package com.sts.training.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class TrainingPlanEvaluationFormBindRequest {
  @NotNull
  private Integer version;
  @Valid @NotNull private List<TrainingPlanEvaluationFormItemRequest> forms;
}

package com.sts.training.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class TrainingTemplateStagesReplaceRequest {
  @NotNull
  private Integer version;
  @Valid private List<TrainingTemplateStageRequest> stages;
}

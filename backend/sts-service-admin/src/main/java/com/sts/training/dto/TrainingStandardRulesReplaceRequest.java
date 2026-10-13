package com.sts.training.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class TrainingStandardRulesReplaceRequest {
  @NotNull
  private Integer version;
  @Valid private List<TrainingStandardRuleRequest> rules;
}

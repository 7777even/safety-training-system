package com.sts.evaluation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class EvaluationFormPublishRequest {
  @NotNull
  private Integer version;
  @NotNull private BigDecimal totalScore;
  private String versionNote;
  @Valid @NotNull private List<EvaluationItemRequest> items;
  @Valid private List<EvaluationScoreRuleRequest> scoreRules;
}

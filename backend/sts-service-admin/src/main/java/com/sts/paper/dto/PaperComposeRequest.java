package com.sts.paper.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class PaperComposeRequest {
  @NotNull private Integer durationSeconds;
  @NotNull private BigDecimal passRatio;
  @NotBlank private String questionOrderMode;
  @NotBlank private String multiSelectScoring;
  private String drawSettingMode = "UNIFIED";
  private String versionNote;
  private Long randomSeed;
  @Valid private List<PaperQuestionItemRequest> questions;
  @Valid private List<PaperDrawRuleRequest> drawRules;
}

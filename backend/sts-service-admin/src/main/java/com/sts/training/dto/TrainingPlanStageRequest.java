package com.sts.training.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class TrainingPlanStageRequest {
  private Long sourceTemplateStageId;
  @NotBlank private String stageName;
  @NotBlank private String stageType;
  private LocalDateTime startTime;
  private LocalDateTime endTime;
  private String location;
  private BigDecimal requiredHours;
  @NotNull private Integer sortOrder;
  private String stageStatus = "PENDING";
  @Valid private List<TrainingPlanContentRequest> contents;
}

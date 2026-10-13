package com.sts.training.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TrainingPlanCreateRequest {
  @NotBlank private String planCode;
  @NotBlank private String planName;
  @NotNull private Long trainingTypeId;
  @NotBlank
  @Pattern(regexp = "INITIAL|REVIEW", message = "培训性质仅支持 INITIAL 或 REVIEW")
  private String trainingNature;
  private Long trainingStandardId;
  private Long sourceTemplateId;
  private Long managerUserId;
  @NotNull private LocalDateTime startTime;
  @NotNull private LocalDateTime endTime;
  private String location;
  private String description;
  @NotNull private Integer status;
}

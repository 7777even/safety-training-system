package com.sts.archive.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ArchiveCreateFromPlanRequest {
  @NotNull
  private Long trainingPlanId;
  @NotBlank
  private String archiveCode;
  @NotBlank
  private String versionReason;
}

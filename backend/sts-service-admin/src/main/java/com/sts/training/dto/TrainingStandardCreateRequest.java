package com.sts.training.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class TrainingStandardCreateRequest {
  @NotBlank private String standardCode;
  @NotBlank private String standardName;
  @NotNull private Long trainingTypeId;
  @NotBlank private String standardVersion;
  private LocalDate validFrom;
  private LocalDate validTo;
  @NotNull private Integer status;
}

package com.sts.training.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class TrainingRewardPenaltyUpdateRequest {
  @NotNull private Integer version;
  @NotBlank private String recordType;
  @NotBlank private String title;
  private String description;
  private BigDecimal scoreChange;
  private LocalDate occurredDate;
  private Long evidenceFileId;
  @NotNull private Integer status;
}

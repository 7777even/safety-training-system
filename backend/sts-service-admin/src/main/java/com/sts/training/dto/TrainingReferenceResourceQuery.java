package com.sts.training.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TrainingReferenceResourceQuery {
  @NotBlank
  private String resourceType;
  private String keyword;
}

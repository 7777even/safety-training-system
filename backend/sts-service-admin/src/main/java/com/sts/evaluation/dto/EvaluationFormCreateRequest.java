package com.sts.evaluation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class EvaluationFormCreateRequest {
  @NotBlank private String formCode;
  @NotBlank private String formName;
  @NotBlank private String formType;
  @NotNull private Integer status;
  @NotBlank private String description;
}

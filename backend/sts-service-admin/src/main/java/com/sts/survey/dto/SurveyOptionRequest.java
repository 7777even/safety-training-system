package com.sts.survey.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SurveyOptionRequest {
  @NotBlank
  private String optionKey;
  @NotBlank
  private String optionContent;
  @NotNull
  private Integer sortOrder;
  @NotNull
  private Integer status;
}

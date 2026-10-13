package com.sts.survey.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SurveySubItemRequest {
  @NotBlank
  private String itemName;
  private String dataType;
  private Integer maxStar;
  @NotNull
  private Integer sortOrder;
  @NotNull
  private Integer status;
}

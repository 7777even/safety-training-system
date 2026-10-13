package com.sts.question.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class QuestionAssetRequest {
  @NotNull private Long assetId;
  @NotBlank private String assetRole;
  private String optionKey = "";
  @NotNull private Integer sortOrder;
}

package com.sts.exam.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SaveAnswerItemRequest {
  @NotBlank private String itemType;
  @NotBlank private String itemKey;
  @NotBlank private String itemValue;
  @NotNull private Integer sortOrder;
}

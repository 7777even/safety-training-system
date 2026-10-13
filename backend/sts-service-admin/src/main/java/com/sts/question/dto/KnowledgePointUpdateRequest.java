package com.sts.question.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class KnowledgePointUpdateRequest {
  @NotBlank private String knowledgeName;
  @NotNull private Integer sortOrder;
  @NotNull private Integer status;
  @NotNull private Integer version;
}

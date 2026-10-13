package com.sts.question.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.List;

@Data
public class QuestionBankUpdateRequest {
  @NotBlank private String bankName;
  private Long bankCategoryId;
  @NotBlank private String difficulty;
  @NotBlank private String questionOrderMode;
  @NotNull private Integer status;
  @NotNull private Integer version;
  private List<Long> knowledgePointIds;
}

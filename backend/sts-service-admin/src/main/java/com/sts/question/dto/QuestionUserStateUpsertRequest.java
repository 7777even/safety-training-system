package com.sts.question.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class QuestionUserStateUpsertRequest {
  @NotNull private Long userId;
  @NotNull private Long questionId;
  private Integer isFavorite;
  private Boolean wrong;
  private Boolean correct;
  private Long lastExamAttemptId;
}

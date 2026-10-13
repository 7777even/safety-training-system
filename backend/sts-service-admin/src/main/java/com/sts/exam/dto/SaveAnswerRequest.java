package com.sts.exam.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.List;

@Data
public class SaveAnswerRequest {
  @NotNull private Long attemptQuestionId;
  private String answerText;
  @Valid private List<SaveAnswerItemRequest> items;
}

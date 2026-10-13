package com.sts.exam.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import java.util.List;

@Data
public class SaveAnswersRequest {
  @NotEmpty @Valid private List<SaveAnswerRequest> answers;
}

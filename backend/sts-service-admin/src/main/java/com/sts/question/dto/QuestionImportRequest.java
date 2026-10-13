package com.sts.question.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.List;

@Data
public class QuestionImportRequest {
  private Long sourceFileId = 0L;
  @NotEmpty @Valid private List<QuestionImportRowRequest> rows;
}

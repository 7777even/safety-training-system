package com.sts.exam.dto;

import com.sts.person.dto.PersonSelectionRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class BindSelectionRequest {
  @Valid
  @NotNull(message = "人员选择不能为空")
  private PersonSelectionRequest selection;
}

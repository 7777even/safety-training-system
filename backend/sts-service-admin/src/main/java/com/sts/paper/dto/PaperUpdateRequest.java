package com.sts.paper.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PaperUpdateRequest {
  @NotBlank private String paperName;
  private Long paperCategoryId;
  private Long coverFileId;
  @NotNull private Integer status;
  @NotNull private Integer version;
}

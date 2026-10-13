package com.sts.paper.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PaperCreateRequest {
  @NotBlank private String paperCode;
  @NotBlank private String paperName;
  @NotBlank private String paperType;
  private Long paperCategoryId;
  private Long coverFileId;
  @NotNull private Integer status;
}

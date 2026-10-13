package com.sts.training.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TrainingAttachmentUpdateRequest {
  @NotNull private Integer version;
  private Long planStageId;
  @NotBlank private String attachmentType;
  @NotNull private Long fileInfoId;
  @NotBlank private String attachmentName;
  private String description;
  @NotNull private Integer sortOrder;
  @NotNull private Integer status;
}

package com.sts.certificate.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.util.List;

@Data
public class CertificateTemplateUpdateRequest {
  @NotNull
  private Integer version;
  @NotBlank
  private String templateName;
  @NotNull
  private Integer status;
  private String description;
  @NotNull
  private Long backgroundFileId;
  @NotNull
  @Positive
  private Integer widthPx;
  @NotNull
  @Positive
  private Integer heightPx;
  private String versionNote;
  @Valid
  @NotNull
  private List<CertificateElementRequest> elements;
}

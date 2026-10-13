package com.sts.certificate.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CertificateTemplateCreateRequest {
  @NotBlank
  private String templateCode;
  @NotBlank
  private String templateName;
  @NotNull
  private Integer status;
  private String description;
}

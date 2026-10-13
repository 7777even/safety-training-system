package com.sts.certificate.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CertificateIssueRequest {
  @NotNull
  private Long certificateTemplateId;
  @NotNull
  private Long templateVersionId;
  private Long certificateRuleId;
  @NotNull
  private Long holderUserId;
  @NotBlank
  private String sourceType;
  @NotNull
  private Long sourceId;
  private Long sourceResultId;
}

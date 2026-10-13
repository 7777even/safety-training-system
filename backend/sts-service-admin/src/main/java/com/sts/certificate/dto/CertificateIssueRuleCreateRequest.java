package com.sts.certificate.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CertificateIssueRuleCreateRequest {
  @NotBlank
  private String ruleName;
  @NotNull
  private Long certificateTemplateId;
  @NotBlank
  private String sourceType;
  @NotNull
  private Long sourceId;
  @NotBlank
  private String conditionType;
  @NotNull
  private Integer isAutoIssue;
  @NotNull
  private Integer status;
}

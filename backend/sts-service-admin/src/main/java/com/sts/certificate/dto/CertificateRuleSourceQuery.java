package com.sts.certificate.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 证书签发规则统一来源候选查询。
 */
@Data
public class CertificateRuleSourceQuery {
  @NotBlank
  private String sourceType;
  private String keyword;
}

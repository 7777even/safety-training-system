package com.sts.certificate.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 更新证书签发规则请求。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CertificateIssueRuleUpdateRequest extends CertificateIssueRuleCreateRequest {
  @NotNull
  private Integer version;
}

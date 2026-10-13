package com.sts.certificate.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 可绑定到证书签发规则的统一来源候选。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CertificateRuleSourceCandidateVO {
  private String sourceType;
  private Long sourceId;
  private String sourceCode;
  private String sourceName;
  private String publishStatus;
}

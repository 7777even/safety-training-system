package com.sts.certificate.vo;

import lombok.Data;

@Data
public class CertificateIssueRuleVO {
  private Long certificateRuleId;
  private Long organizationId;
  private String ruleName;
  private Long certificateTemplateId;
  private String certificateTemplateName;
  private String sourceType;
  private Long sourceId;
  private String sourceName;
  private String conditionType;
  private Integer isAutoIssue;
  private Integer status;
  private Integer version;
}

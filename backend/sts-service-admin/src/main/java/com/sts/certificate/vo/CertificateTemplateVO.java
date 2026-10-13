package com.sts.certificate.vo;

import lombok.Data;

@Data
public class CertificateTemplateVO {
  private Long certificateTemplateId;
  private Long organizationId;
  private String templateCode;
  private String templateName;
  private Integer currentVersionNo;
  private Integer status;
  private Integer version;
  private String description;
  /**
   * 当前已发布且用于新签发的不可变版本。
   */
  private CertificateTemplateVersionVO currentPublishedVersion;
  /**
   * 当前可覆盖的下一版本设计草稿。
   */
  private CertificateTemplateVersionVO draftVersion;
}

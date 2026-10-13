package com.sts.certificate.vo;

import lombok.Data;

import java.util.List;

/**
 * 证书模板设计版本及其完整元素。
 */
@Data
public class CertificateTemplateVersionVO {
  private Long templateVersionId;
  private Integer versionNo;
  private Long backgroundFileId;
  private Integer widthPx;
  private Integer heightPx;
  private String publishedStatus;
  private String versionNote;
  private List<CertificateTemplateElementVO> elements;
}

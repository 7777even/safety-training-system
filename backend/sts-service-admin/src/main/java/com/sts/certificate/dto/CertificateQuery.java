package com.sts.certificate.dto;

import lombok.Data;

@Data
public class CertificateQuery {
  private Long holderUserId;
  private String sourceType;
  private Long sourceId;
  private String certificateStatus;
}

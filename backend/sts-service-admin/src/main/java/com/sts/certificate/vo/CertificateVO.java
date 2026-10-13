package com.sts.certificate.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CertificateVO {
  private Long certificateId;
  private Long organizationId;
  private String certificateNo;
  private Long holderUserId;
  private String sourceType;
  private Long sourceId;
  /** 来源课程、考试或培训计划的当前名称；来源不可见时为空。 */
  private String sourceName;
  private String certificateStatus;
  private LocalDateTime issuedTime;
  /**
   * 真实证书文件 ID，禁止占位。
   */
  private Long certificateFileId;
}

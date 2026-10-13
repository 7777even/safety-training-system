package com.sts.certificate.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sts.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sts_certificate_certificate")
public class CertificateCertificate extends BaseEntity {
  @TableId(type = IdType.AUTO)
  private Long certificateId;
  private Long organizationId;
  private String certificateNo;
  private Long certificateRuleId;
  private Long certificateTemplateId;
  private Long templateVersionId;
  private Long holderUserId;
  private String sourceType;
  private Long sourceId;
  private Long sourceResultId;
  private LocalDateTime issuedTime;
  private LocalDate validFrom;
  private LocalDate validTo;
  private Long certificateFileId;
  private String contentSnapshotJson;
  private String snapshotHash;
  private String certificateStatus;
  private LocalDateTime revokedTime;
}

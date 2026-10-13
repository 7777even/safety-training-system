package com.sts.certificate.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sts_certificate_revocation")
public class CertificateRevocation {
  @TableId(type = IdType.AUTO)
  private Long certificateRevocationId;
  private Long organizationId;
  private Long certificateId;
  private Long revokedBy;
  private LocalDateTime revokedTime;
  private String reason;
  private String beforeStatus;
  private String afterStatus;
  private Long createBy;
  private LocalDateTime createTime;
}

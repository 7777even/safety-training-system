package com.sts.certificate.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sts.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sts_certificate_template")
public class CertificateTemplate extends BaseEntity {
  @TableId(type = IdType.AUTO)
  private Long certificateTemplateId;
  private Long organizationId;
  private String templateCode;
  private String templateName;
  private Integer currentVersionNo;
  private Integer status;
  private String description;
}

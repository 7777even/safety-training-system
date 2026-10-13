package com.sts.certificate.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sts_certificate_template_version")
public class CertificateTemplateVersion {
  @TableId(type = IdType.AUTO)
  private Long templateVersionId;
  private Long certificateTemplateId;
  private Integer versionNo;
  private Long backgroundFileId;
  private Integer widthPx;
  private Integer heightPx;
  private String publishedStatus;
  private Long publishedBy;
  private LocalDateTime publishedTime;
  private String versionNote;
  private Long createBy;
  private LocalDateTime createTime;
}

package com.sts.certificate.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sts.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sts_certificate_template_element")
public class CertificateTemplateElement extends BaseEntity {
  @TableId(type = IdType.AUTO)
  private Long templateElementId;
  private Long templateVersionId;
  private String elementType;
  private String variableCode;
  private String staticContent;
  private Integer xPosition;
  private Integer yPosition;
  private Integer width;
  private Integer height;
  private String fontFamily;
  private Integer fontSize;
  private String fontColor;
  private Integer sortOrder;
  private Integer status;
}

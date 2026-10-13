package com.sts.certificate.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sts.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sts_certificate_issue_rule")
public class CertificateIssueRule extends BaseEntity {
  @TableId(type = IdType.AUTO)
  private Long certificateRuleId;
  private Long organizationId;
  private String ruleName;
  private Long certificateTemplateId;
  private String sourceType;
  private Long sourceId;
  private String conditionType;
  private BigDecimal conditionValue;
  private String collectionInstruction;
  private Integer isAutoIssue;
  private LocalDateTime validFrom;
  private LocalDateTime validTo;
  private Integer status;
}

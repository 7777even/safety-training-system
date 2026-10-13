package com.sts.org.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 租户信息。
 */
@Data
public class OrganizationVO {
  private Long organizationId;
  /**
   * 企业编码，登录入口标识。
   */
  private String orgCode;
  private String orgName;
  /** 租户类型：ENTERPRISE 企业租户 / REGULATOR 监管平台租户。 */
  private String orgType;
  private Long contactUserId;
  private Integer status;
  private String description;
  private Integer version;
  private LocalDateTime createTime;
  private LocalDateTime updateTime;
}

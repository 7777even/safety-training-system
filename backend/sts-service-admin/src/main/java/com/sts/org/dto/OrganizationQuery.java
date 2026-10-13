package com.sts.org.dto;

import lombok.Data;

/**
 * 租户分页查询条件。
 */
@Data
public class OrganizationQuery {
  /**
   * 按企业编码或租户名称模糊匹配。
   */
  private String keyword;
  /** 租户类型：ENTERPRISE / REGULATOR。 */
  private String orgType;
  private Integer status;
}

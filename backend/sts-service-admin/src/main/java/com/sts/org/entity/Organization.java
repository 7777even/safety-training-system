package com.sts.org.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sts.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 租户主数据表。
 * <p>
 * 每个企业或培训机构是一个业务租户，另有至多一个 {@code REGULATOR} 监管平台租户。
 * 租户之间没有层级关系，{@code organization_id} 即全系统租户 ID。
 * 详见 openspec/changes/2026-08-06-rebuild-multi-tenant-isolation/design.md §1。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sts_org_organization")
public class Organization extends BaseEntity {
  @TableId(type = IdType.AUTO)
  private Long organizationId;
  /**
   * 企业编码，登录入口标识，全局唯一，创建后不可修改。
   */
  private String orgCode;
  private String orgName;
  /**
   * 租户类型：ENTERPRISE 企业 / TRAINING_INSTITUTION 培训机构 / REGULATOR 监管平台。
   */
  private String orgType;
  private Long contactUserId;
  private Integer status;
  private String description;
}

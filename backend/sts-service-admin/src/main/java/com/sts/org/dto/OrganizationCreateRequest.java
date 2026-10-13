package com.sts.org.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

/**
 * 创建租户请求。superAdmin 或监管租户可调用；监管不得创建 REGULATOR 租户。
 */
@Data
public class OrganizationCreateRequest {

  /**
   * 企业编码，登录入口标识，全局唯一，创建后不可修改。
   */
  @NotBlank(message = "企业编码不能为空")
  @Size(min = 2, max = 64, message = "企业编码长度必须为2到64个字符")
  @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "企业编码只能包含字母、数字、下划线和短横线")
  private String orgCode;

  @NotBlank(message = "租户名称不能为空")
  @Size(max = 100, message = "租户名称不能超过100个字符")
  private String orgName;

  /**
   * 租户类型：ENTERPRISE 企业 / TRAINING_INSTITUTION 培训机构 / REGULATOR 监管平台。
   */
  @NotBlank(message = "租户类型不能为空")
  @Pattern(regexp = "^(ENTERPRISE|TRAINING_INSTITUTION|REGULATOR)$", message = "租户类型不合法")
  private String orgType;

  private Long contactUserId;

  @Min(value = 0, message = "租户状态只能为0或1")
  @Max(value = 1, message = "租户状态只能为0或1")
  private Integer status;

  @Size(max = 500, message = "租户描述不能超过500个字符")
  private String description;

  /**
   * 初始管理员工号，默认 {@code admin}；在新租户内唯一。
   */
  @Size(min = 2, max = 64, message = "管理员工号长度必须为2到64个字符")
  @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "管理员工号只能包含字母、数字、下划线和短横线")
  private String adminUserCode = "admin";

  @Size(max = 64, message = "管理员名称不能超过64个字符")
  private String adminUserName = "管理员";

  /**
   * 初始管理员密码（前端 SHA-256 离散串）。开户必须建立可登录的管理员，否则新租户无人可进入。
   */
  @NotBlank(message = "初始管理员密码不能为空")
  private String adminPassword;
}

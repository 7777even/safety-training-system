package com.sts.org.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

/**
 * 修改租户请求。superAdmin 或监管租户可调用。
 * <p>
 * 企业编码不可修改。企业与培训机构类型可由管理员明确改写，监管平台类型不可改。
 * 停用租户通过 {@code status=0} 表达。
 */
@Data
public class OrganizationUpdateRequest {

  @NotBlank(message = "租户名称不能为空")
  @Size(max = 100, message = "租户名称不能超过100个字符")
  private String orgName;

  /**
   * 可选。仅允许在 ENTERPRISE 与 TRAINING_INSTITUTION 之间改写，不得改为 REGULATOR。
   */
  @Pattern(regexp = "^(ENTERPRISE|TRAINING_INSTITUTION)$", message = "租户类型不合法")
  private String orgType;

  private Long contactUserId;

  @NotNull(message = "租户状态不能为空")
  @Min(value = 0, message = "租户状态只能为0或1")
  @Max(value = 1, message = "租户状态只能为0或1")
  private Integer status;

  @Size(max = 500, message = "租户描述不能超过500个字符")
  private String description;

  @NotNull(message = "版本号不能为空")
  private Integer version;
}

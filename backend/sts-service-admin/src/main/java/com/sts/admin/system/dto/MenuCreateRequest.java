package com.sts.admin.system.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class MenuCreateRequest {
  @NotNull(message = "父菜单ID不能为空")
  private Long parentId;

  @NotBlank(message = "菜单名称不能为空")
  private String menuName;

  private String path;
  private String component;
  private String perms;
  private String icon;
  private Integer sortOrder = 0;

  @NotNull(message = "可见状态不能为空")
  @Min(value = 0, message = "可见状态只能是 0 或 1")
  @Max(value = 1, message = "可见状态只能是 0 或 1")
  private Integer isVisible = 1;

  @NotBlank(message = "菜单类型不能为空")
  @Pattern(regexp = "[DMCF]", message = "菜单类型只能是 D、M、C 或 F")
  private String menuType;

  private Long moduleId;
}

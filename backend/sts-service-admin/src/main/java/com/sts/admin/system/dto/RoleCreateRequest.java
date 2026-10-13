package com.sts.admin.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class RoleCreateRequest {
    @NotNull(message = "机构ID不能为空")
    private Long organizationId;

    @NotBlank(message = "角色名称不能为空")
    private String roleName;

    @NotBlank(message = "角色编码不能为空")
    private String roleCode;

    private String roleLevel = "ADMIN";

    private String description = "";

    private Integer status = 1;

    private List<Long> menuIds;

}

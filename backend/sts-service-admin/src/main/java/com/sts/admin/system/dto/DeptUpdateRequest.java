package com.sts.admin.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DeptUpdateRequest {
    @NotNull(message = "父部门ID不能为空")
    private Long parentId;

    @NotBlank(message = "部门编码不能为空")
    private String deptCode;

    @NotBlank(message = "部门名称不能为空")
    private String deptName;

    private String deptType = "DEPARTMENT";
    private Integer sortOrder;
    private String leader;
    private String phone;
    private String description = "";

    @NotNull(message = "状态不能为空")
    private Integer status;

    @NotNull(message = "版本号不能为空")
    private Integer version;
}

package com.sts.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sts.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("us_sys_role")
public class SysRole extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long roleId;
    private Long organizationId;
    private String roleName;
    private String roleCode;
    private String roleLevel;
    private Integer isBuiltin;
    private Integer status;
    private String description;
}

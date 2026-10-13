package com.sts.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sts.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("us_sys_dept")
public class SysDept extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long deptId;
    private Long organizationId;
    private Long parentId;
    private String ancestors;
    private String deptCode;
    private String deptName;
    private String deptType;
    private Integer sortOrder;
    private String leader;
    private String phone;
    private String description;
    private Integer status;
}

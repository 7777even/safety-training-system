package com.sts.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sts.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("us_sys_user")
public class SysUser extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long userId;
  /**
   * 所属租户 ID；superAdmin 之外的账号必须归属唯一租户。
   */
  private Long organizationId;
  /**
   * 租户内工号，唯一键为 (organization_id, user_code, deleted)。
   */
    private String userCode;
    private String userName;
  /**
   * 成员类型：INTERNAL 内部员工 / EXTERNAL 外部人员 / REGULATOR 监管人员。
   */
  private String memberType;
  private String email;
  /**
   * 职务。考生档案快照等业务从此处取值。
   */
  private String jobTitle;
  private java.time.LocalDate entryDate;
    private String phone;
    private Integer status;
  /**
   * 所属部门；可空表示企业开户管理员尚未自行建立并加入部门。
   */
    private Long deptId;
}

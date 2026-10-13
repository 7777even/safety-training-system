package com.sts.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sts.common.entity.BaseAuditEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("us_sys_login_log")
public class SysLoginLog extends BaseAuditEntity {
    @TableId(type = IdType.AUTO)
    private Long loginLogId;
  /**
   * 所属租户 ID；登录成功时为所选登录租户，租户无法解析的失败尝试记为 0。
   */
  private Long organizationId;
    private String userCode;
    private String ipaddr;
    private String loginLocation;
    private String browser;
    private String os;
    private Integer status;
    private String msg;
    private java.time.LocalDateTime loginTime;
}

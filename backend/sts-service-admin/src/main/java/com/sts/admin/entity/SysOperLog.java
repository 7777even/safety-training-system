package com.sts.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sts.common.entity.BaseAuditEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("us_sys_oper_log")
public class SysOperLog extends BaseAuditEntity {
    @TableId(type = IdType.AUTO)
    private Long operLogId;
  /**
   * 所属租户 ID；已登录操作（含 superAdmin）记录登录租户，无法解析租户时记 0。
   * <p>
   * MUST 在发布事件时显式赋值：日志经 {@code @Async} 线程落库，
   * 租户 ThreadLocal 不跨线程，插件不会为其注入该列，漏填会直接违反非空约束。
   */
  private Long organizationId;
    private String title;
    private Integer businessType;
    private String method;
    private String requestMethod;
    private String operatorCode;
    private String operIp;
    private String operUrl;
    private String operParam;
    private String jsonResult;
    private Integer status;
    private String errorMsg;
}

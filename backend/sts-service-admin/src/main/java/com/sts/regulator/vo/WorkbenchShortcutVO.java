package com.sts.regulator.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkbenchShortcutVO {
  /**
   * 入口编码：CERTIFICATE / COURSE / DATA_CENTER
   */
  private String code;
  private String title;
  /**
   * 前端路由，不授予额外权限
   */
  private String path;
  /**
   * 目标模块权限码；无权限时仍返回，由前端隐藏或后端 isGranted=false
   */
  private String permission;
  private boolean granted;
}

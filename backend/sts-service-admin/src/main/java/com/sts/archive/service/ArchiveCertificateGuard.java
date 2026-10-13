package com.sts.archive.service;

import cn.dev33.satoken.stp.StpUtil;
import org.springframework.stereotype.Service;

/**
 * 档案证书清单的读取权限判定。
 * <p>
 * 档案三视角共用一份事实，但证书编号与签发时间属于证书域数据，档案入口 MUST 与
 * {@code certificate:cert:list} 保持同一权限点；缺少该权限时档案响应不返回证书清单字段，
 * 而不是"返回清单再禁用下载"。未登录按无权限处理（{@code hasPermission} 在未登录时会抛异常，
 * 因此先判登录再判权限）。
 */
@Service
public class ArchiveCertificateGuard {

  /** 与证书列表入口同一权限点，避免出现第二套判定口径。 */
  public static final String CERTIFICATE_LIST_PERMISSION = "certificate:cert:list";

  /**
   * 当前调用方是否可读取档案中的证书清单。
   *
   * @return 已登录且具备 {@code certificate:cert:list} 时为 true
   */
  public boolean canListCertificates() {
    return StpUtil.isLogin() && StpUtil.hasPermission(CERTIFICATE_LIST_PERMISSION);
  }
}

package com.sts.ops.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.sts.ops.service.OpsWorkbenchService;
import com.sts.ops.vo.OpsWorkbenchSummaryVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 企业管理员工作台。
 */
@RestController
@RequiredArgsConstructor
public class OpsController {

  private final OpsWorkbenchService workbenchService;

  /**
   * 查询企业工作台：权限化入口、真实概览和分类待办。
   */
  @GetMapping("/ops/workbench/enterprise")
  @SaCheckPermission("ops:workbench:enterprise")
  public OpsWorkbenchSummaryVO enterpriseWorkbench() {
    return workbenchService.enterpriseSummary();
  }
}

package com.sts.admin.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.sts.admin.aop.annotation.BusinessType;
import com.sts.admin.aop.annotation.OperLog;
import com.sts.admin.system.dto.LoginLogQuery;
import com.sts.admin.system.service.SysLoginLogService;
import com.sts.admin.system.vo.LoginLogVO;
import com.sts.common.api.PageRequest;
import com.sts.common.api.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户登录日志查询与清理接口。
 */
@RestController
@RequestMapping("/system/login-logs")
@RequiredArgsConstructor
public class SysLoginLogController {

    private final SysLoginLogService loginLogService;

  /**
   * 分页查询登录日志。
   */
    @GetMapping
    @SaCheckPermission("sys:loginlog:list")
    public PageResult<LoginLogVO> pageLoginLogs(PageRequest<LoginLogQuery> request) {
        return loginLogService.pageLoginLogs(request);
    }

  /**
   * 删除指定日志。
   */
  @DeleteMapping("/{loginLogId}")
    @SaCheckPermission("sys:loginlog:remove")
    @OperLog(title = "登录日志", businessType = BusinessType.DELETE)
    public void deleteById(@PathVariable Long loginLogId) {
        loginLogService.deleteById(loginLogId);
    }

    /**
     * 批量删除登录日志。
     */
    @DeleteMapping
    @SaCheckPermission("sys:loginlog:remove")
    @OperLog(title = "登录日志", businessType = BusinessType.DELETE)
    public void batchDelete(@RequestBody List<Long> ids) {
        loginLogService.batchDelete(ids);
    }

    /**
     * 清空全部日志。
     */
    @DeleteMapping("/clean")
    @SaCheckPermission("sys:loginlog:clean")
    @OperLog(title = "登录日志", businessType = BusinessType.CLEAN)
    public void cleanAll() {
        loginLogService.cleanAll();
    }
}

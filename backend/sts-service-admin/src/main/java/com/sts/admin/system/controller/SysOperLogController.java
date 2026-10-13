package com.sts.admin.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.sts.admin.aop.annotation.BusinessType;
import com.sts.admin.aop.annotation.OperLog;
import com.sts.admin.system.dto.OperLogQuery;
import com.sts.admin.system.service.SysOperLogService;
import com.sts.admin.system.vo.OperLogVO;
import com.sts.common.api.PageRequest;
import com.sts.common.api.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 系统操作日志查询与清理接口。
 */
@RestController
@RequestMapping("/system/oper-logs")
@RequiredArgsConstructor
public class SysOperLogController {

    private final SysOperLogService operLogService;

  /**
   * 分页查询操作日志。
   */
    @GetMapping
    @SaCheckPermission("sys:operlog:list")
    public PageResult<OperLogVO> pageOperLogs(PageRequest<OperLogQuery> request) {
        return operLogService.pageOperLogs(request);
    }

  /**
   * 查询操作日志详情。
   */
  @GetMapping("/{operLogId}")
    @SaCheckPermission("sys:operlog:query")
    public OperLogVO detail(@PathVariable Long operLogId) {
        return operLogService.detail(operLogId);
    }

    /**
     * 批量删除操作日志。
     */
    @DeleteMapping
    @SaCheckPermission("sys:operlog:remove")
    @OperLog(title = "操作日志", businessType = BusinessType.DELETE)
    public void batchDelete(@RequestBody List<Long> ids) {
        operLogService.batchDelete(ids);
    }

    /**
     * 清空全部日志。
     */
    @DeleteMapping("/clean")
    @SaCheckPermission("sys:operlog:remove")
    @OperLog(title = "操作日志", businessType = BusinessType.CLEAN)
    public void cleanAll() {
        operLogService.cleanAll();
    }
}

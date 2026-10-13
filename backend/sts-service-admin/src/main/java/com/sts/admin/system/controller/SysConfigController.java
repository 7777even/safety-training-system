package com.sts.admin.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.sts.admin.aop.annotation.BusinessType;
import com.sts.admin.aop.annotation.OperLog;
import com.sts.admin.system.dto.ConfigCreateRequest;
import com.sts.admin.system.dto.ConfigQuery;
import com.sts.admin.system.dto.ConfigUpdateRequest;
import com.sts.admin.system.service.SysConfigService;
import com.sts.admin.system.vo.ConfigVO;
import com.sts.common.api.PageRequest;
import com.sts.common.api.PageResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 系统参数配置及配置缓存管理接口。
 */
@RestController
@RequestMapping("/system/configs")
@RequiredArgsConstructor
public class SysConfigController {

    private final SysConfigService configService;

  /**
   * 分页查询系统参数。
   */
    @GetMapping
    @SaCheckPermission("sys:config:list")
    public PageResult<ConfigVO> pageConfigs(PageRequest<ConfigQuery> request) {
        return configService.pageConfigs(request);
    }

  /**
   * 创建系统参数。
   */
  @PostMapping
    @OperLog(title = "参数配置", businessType = BusinessType.INSERT)
    @SaCheckPermission("sys:config:add")
    public Long createConfig(@RequestBody @Valid ConfigCreateRequest request) {
        return configService.createConfig(request);
    }

    /**
     * 更新系统参数。
     */
    @PutMapping("/{configId}")
    @OperLog(title = "参数配置", businessType = BusinessType.UPDATE)
    @SaCheckPermission("sys:config:edit")
    public void updateConfig(@PathVariable Long configId, @RequestBody @Valid ConfigUpdateRequest request) {
        configService.updateConfig(configId, request);
    }

    /**
     * 删除系统参数。
     */
    @DeleteMapping("/{configId}")
    @OperLog(title = "参数配置", businessType = BusinessType.DELETE)
    @SaCheckPermission("sys:config:delete")
    public void deleteConfig(@PathVariable Long configId) {
        configService.deleteConfig(configId);
    }

    /**
     * 清理系统参数缓存。
     */
    @DeleteMapping("/cache")
    @OperLog(title = "参数配置", businessType = BusinessType.CLEAN)
    @SaCheckPermission("sys:config:clearCache")
    public void clearCache() {
        configService.clearCache();
    }
}

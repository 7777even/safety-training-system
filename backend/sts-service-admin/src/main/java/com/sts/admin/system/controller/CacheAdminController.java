package com.sts.admin.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.sts.admin.aop.annotation.BusinessType;
import com.sts.admin.aop.annotation.OperLog;
import com.sts.admin.system.dto.CacheClearRequest;
import com.sts.admin.system.service.CacheAdminService;
import com.sts.admin.system.vo.CacheRegistryItemVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 系统缓存项查询及指定缓存清理接口。
 */
@RestController
@RequestMapping("/system/cache")
@RequiredArgsConstructor
public class CacheAdminController {

    private final CacheAdminService cacheAdminService;

  /**
   * 查询可管理的缓存清单。
   */
    @GetMapping("/registry")
    @SaCheckPermission("sys:cache:list")
    public List<CacheRegistryItemVO> registry() {
        return cacheAdminService.listRegistry();
    }

  /**
   * 清理指定缓存。
   */
  @PostMapping("/clear")
    @SaCheckPermission("sys:cache:clear")
    @OperLog(title = "缓存管理", businessType = BusinessType.CLEAN)
    public void clear(@RequestBody @Valid CacheClearRequest request) {
        cacheAdminService.clear(request);
    }
}

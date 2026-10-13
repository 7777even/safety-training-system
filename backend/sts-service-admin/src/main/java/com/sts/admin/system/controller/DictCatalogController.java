package com.sts.admin.system.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.sts.admin.system.service.DictCatalogService;
import com.sts.common.api.dict.DictCatalogVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 供客户端加载系统字典目录的聚合查询接口。
 */
@RestController
@RequestMapping("/system/dict")
@RequiredArgsConstructor
public class DictCatalogController {

    private final DictCatalogService dictCatalogService;

  /**
   * 查询字典目录。
   */
    @GetMapping("/catalog")
    public DictCatalogVO getCatalog(@RequestParam(required = false) String sinceVersion) {
        StpUtil.checkLogin();
        return dictCatalogService.getCatalog(sinceVersion);
    }
}

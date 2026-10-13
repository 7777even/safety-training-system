package com.sts.admin.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.sts.admin.aop.annotation.BusinessType;
import com.sts.admin.aop.annotation.OperLog;
import com.sts.admin.system.dto.DictDataCreateRequest;
import com.sts.admin.system.dto.DictDataQuery;
import com.sts.admin.system.dto.DictDataUpdateRequest;
import com.sts.admin.system.service.SysDictDataService;
import com.sts.admin.system.vo.DictDataVO;
import com.sts.common.api.PageRequest;
import com.sts.common.api.PageResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 系统字典数据项管理与查询接口。
 */
@RestController
@RequestMapping("/system/dict/data")
@RequiredArgsConstructor
public class SysDictDataController {

    private final SysDictDataService dictDataService;

  /**
   * 分页查询字典数据。
   */
    @GetMapping
    @SaCheckPermission("sys:dict:list")
    public PageResult<DictDataVO> pageDictData(PageRequest<DictDataQuery> request) {
        return dictDataService.pageDictData(request);
    }

  /**
   * 查询字典数据列表。
   */
  @GetMapping("/{dictType}")
    @SaCheckPermission("sys:dict:list")
    public List<DictDataVO> listByDictType(@PathVariable String dictType) {
        return dictDataService.listByDictType(dictType);
    }

    /**
     * 创建字典数据。
     */
    @PostMapping
    @SaCheckPermission("sys:dict:add")
    @OperLog(title = "字典数据", businessType = BusinessType.INSERT)
    public Long createDictData(@RequestBody @Valid DictDataCreateRequest request) {
        return dictDataService.createDictData(request);
    }

    /**
     * 更新字典数据。
     */
    @PutMapping("/{dictDataId}")
    @SaCheckPermission("sys:dict:edit")
    @OperLog(title = "字典数据", businessType = BusinessType.UPDATE)
    public void updateDictData(@PathVariable Long dictDataId, @RequestBody @Valid DictDataUpdateRequest request) {
        dictDataService.updateDictData(dictDataId, request);
    }

    /**
     * 删除字典数据。
     */
    @DeleteMapping("/{dictDataId}")
    @SaCheckPermission("sys:dict:delete")
    @OperLog(title = "字典数据", businessType = BusinessType.DELETE)
    public void deleteDictData(@PathVariable Long dictDataId) {
        dictDataService.deleteDictData(dictDataId);
    }
}

package com.sts.admin.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.sts.admin.aop.annotation.BusinessType;
import com.sts.admin.aop.annotation.OperLog;
import com.sts.admin.system.dto.DictTypeCreateRequest;
import com.sts.admin.system.dto.DictTypeQuery;
import com.sts.admin.system.dto.DictTypeUpdateRequest;
import com.sts.admin.system.service.SysDictTypeService;
import com.sts.admin.system.vo.DictTypeVO;
import com.sts.common.api.PageRequest;
import com.sts.common.api.PageResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 系统字典类型管理接口。
 */
@RestController
@RequestMapping("/system/dict/types")
@RequiredArgsConstructor
public class SysDictTypeController {

    private final SysDictTypeService dictTypeService;

  /**
   * 分页查询字典类型。
   */
    @GetMapping
    @SaCheckPermission("sys:dict:list")
    public PageResult<DictTypeVO> pageDictTypes(PageRequest<DictTypeQuery> request) {
        return dictTypeService.pageDictTypes(request);
    }

  /**
   * 创建字典类型。
   */
  @PostMapping
    @OperLog(title = "字典管理", businessType = BusinessType.INSERT)
    @SaCheckPermission("sys:dict:add")
    public Long createDictType(@RequestBody @Valid DictTypeCreateRequest request) {
        return dictTypeService.createDictType(request);
    }

    /**
     * 更新字典类型。
     */
    @PutMapping("/{dictTypeId}")
    @OperLog(title = "字典管理", businessType = BusinessType.UPDATE)
    @SaCheckPermission("sys:dict:edit")
    public void updateDictType(@PathVariable Long dictTypeId, @RequestBody @Valid DictTypeUpdateRequest request) {
        dictTypeService.updateDictType(dictTypeId, request);
    }

    /**
     * 删除字典类型。
     */
    @DeleteMapping("/{dictTypeId}")
    @OperLog(title = "字典管理", businessType = BusinessType.DELETE)
    @SaCheckPermission("sys:dict:delete")
    public void deleteDictType(@PathVariable Long dictTypeId) {
        dictTypeService.deleteDictType(dictTypeId);
    }
}

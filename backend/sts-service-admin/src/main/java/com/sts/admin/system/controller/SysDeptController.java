package com.sts.admin.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.sts.admin.aop.annotation.BusinessType;
import com.sts.admin.aop.annotation.OperLog;
import com.sts.admin.system.dto.DeptCreateRequest;
import com.sts.admin.system.dto.DeptUpdateRequest;
import com.sts.admin.system.service.SysDeptService;
import com.sts.admin.system.vo.DeptTreeVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 系统部门树及部门信息管理接口。
 */
@RestController
@RequestMapping("/system/depts")
@RequiredArgsConstructor
public class SysDeptController {

    private final SysDeptService deptService;

  /**
   * 查询部门树。
   */
    @GetMapping("/tree")
    @SaCheckPermission("sys:dept:list")
    public List<DeptTreeVO> getDeptTree(
        @RequestParam(required = false) String deptName,
        @RequestParam(required = false) Integer status) {
        return deptService.getDeptTree(deptName, status);
    }

  /**
   * 创建部门。
   */
  @PostMapping
    @OperLog(title = "部门管理", businessType = BusinessType.INSERT)
    @SaCheckPermission("sys:dept:add")
    public Long createDept(@RequestBody @Valid DeptCreateRequest request) {
        return deptService.createDept(request);
    }

    /**
     * 更新部门。
     */
    @PutMapping("/{deptId}")
    @OperLog(title = "部门管理", businessType = BusinessType.UPDATE)
    @SaCheckPermission("sys:dept:edit")
    public void updateDept(@PathVariable Long deptId, @RequestBody @Valid DeptUpdateRequest request) {
        deptService.updateDept(deptId, request);
    }

    /**
     * 删除部门。
     */
    @DeleteMapping("/{deptId}")
    @OperLog(title = "部门管理", businessType = BusinessType.DELETE)
    @SaCheckPermission("sys:dept:delete")
    public void deleteDept(@PathVariable Long deptId) {
        deptService.deleteDept(deptId);
    }
}

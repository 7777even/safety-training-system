package com.sts.admin.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.sts.admin.aop.annotation.BusinessType;
import com.sts.admin.aop.annotation.OperLog;
import com.sts.admin.system.dto.ResetPasswordRequest;
import com.sts.admin.system.dto.UserCreateRequest;
import com.sts.admin.system.dto.UserExportData;
import com.sts.admin.system.dto.UserImportResult;
import com.sts.admin.system.dto.UserQuery;
import com.sts.admin.system.dto.UserUpdateRequest;
import com.sts.admin.system.service.SysUserService;
import com.sts.admin.system.vo.UserVO;
import com.sts.common.api.PageRequest;
import com.sts.common.api.PageResult;
import com.sts.regulation.export.Utf8CsvExporter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 系统用户、用户资料及密码管理接口。
 */
@RestController
@RequestMapping("/system/users")
@RequiredArgsConstructor
public class SysUserController {

    private final SysUserService userService;

  /**
   * 查询用户列表。
   */
    @GetMapping
    @SaCheckPermission("sys:user:list")
    public PageResult<UserVO> listUsers(PageRequest<UserQuery> request) {
        return userService.pageUsers(request);
    }

  /**
   * 创建用户。
   */
  @OperLog(title = "用户管理", businessType = BusinessType.INSERT)
    @PostMapping
    @SaCheckPermission("sys:user:add")
    public Long createUser(@RequestBody @Valid UserCreateRequest request) {
        return userService.createUser(request);
    }

    /**
     * 更新用户。
     */
    @OperLog(title = "用户管理", businessType = BusinessType.UPDATE)
    @PutMapping("/{userId}")
    @SaCheckPermission("sys:user:edit")
    public void updateUser(@PathVariable Long userId, @RequestBody @Valid UserUpdateRequest request) {
        userService.updateUser(userId, request);
    }

    /**
     * 删除用户。
     */
    @OperLog(title = "用户管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{userId}")
    @SaCheckPermission("sys:user:delete")
    public void deleteUser(@PathVariable Long userId) {
        userService.deleteUser(userId);
    }

    /**
     * 重置指定用户的密码。
     */
    @OperLog(title = "用户管理", businessType = BusinessType.UPDATE, saveRequestData = false)
    @PutMapping("/{userId}/reset-password")
    @SaCheckPermission("sys:user:resetPwd")
    public void resetPassword(@PathVariable Long userId, @RequestBody @Valid ResetPasswordRequest request) {
        userService.resetPassword(userId, request);
    }

    /**
     * 查询当前用户资料。
     */
    @GetMapping("/profile")
    public UserVO getUserProfile() {
        return userService.getUserProfile();
    }

    /**
     * 导出用户为 CSV（当前租户范围内，沿用现有筛选条件）。
     */
    @GetMapping("/export")
    @SaCheckPermission("sys:user:export")
    public ResponseEntity<byte[]> exportUsers(UserQuery query) {
        UserExportData data = userService.exportUsers(query);
        String filterSummary = query == null ? "" : "userCode=" + query.getUserCode() + ";userName=" + query.getUserName();
        return Utf8CsvExporter.download("users.csv", filterSummary, data.headers(), data.rows());
    }

    /**
     * 从 CSV 批量导入用户（当前租户范围内，按部门编码归属）。
     */
    @OperLog(title = "用户管理", businessType = BusinessType.INSERT)
    @PostMapping("/import")
    @SaCheckPermission("sys:user:import")
    public UserImportResult importUsers(@RequestParam("file") MultipartFile file) {
        return userService.importUsers(file);
    }
}

package com.sts.admin.system.service;

import com.sts.admin.system.dto.ResetPasswordRequest;
import com.sts.admin.system.dto.UserCreateRequest;
import com.sts.admin.system.dto.UserExportData;
import com.sts.admin.system.dto.UserImportResult;
import com.sts.admin.system.dto.UserQuery;
import com.sts.admin.system.dto.UserUpdateRequest;
import com.sts.admin.system.vo.UserVO;
import com.sts.common.api.PageRequest;
import com.sts.common.api.PageResult;
import org.springframework.web.multipart.MultipartFile;

public interface SysUserService {

    PageResult<UserVO> pageUsers(PageRequest<UserQuery> request);

    Long createUser(UserCreateRequest request);

    void updateUser(Long userId, UserUpdateRequest request);

    void deleteUser(Long userId);

    void resetPassword(Long userId, ResetPasswordRequest request);

    /**
     * 获取当前登录用户的最新配置信息（含角色与最新权限）
     */
    UserVO getUserProfile();

    /**
     * 按当前筛选（当前租户范围内）导出用户为 CSV。
     */
    UserExportData exportUsers(UserQuery query);

    /**
     * 从 CSV 批量导入用户（当前租户范围内，按部门编码归属）。
     */
    UserImportResult importUsers(MultipartFile file);
}

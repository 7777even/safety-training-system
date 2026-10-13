package com.sts.admin.system.service;

import com.sts.admin.system.dto.ResetPasswordRequest;
import com.sts.admin.system.dto.UserCreateRequest;
import com.sts.admin.system.dto.UserQuery;
import com.sts.admin.system.dto.UserUpdateRequest;
import com.sts.admin.system.vo.UserVO;
import com.sts.common.api.PageRequest;
import com.sts.common.api.PageResult;

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
}

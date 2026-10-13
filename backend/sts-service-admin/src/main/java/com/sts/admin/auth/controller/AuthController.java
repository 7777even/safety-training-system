package com.sts.admin.auth.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.sts.admin.auth.config.AuthConfigProperties;
import com.sts.admin.auth.dto.LoginRequest;
import com.sts.admin.auth.dto.LoginVO;
import com.sts.admin.auth.service.AuthApplicationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 登录方式查询、用户登录及退出接口。
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthConfigProperties authConfigProperties;
    private final AuthApplicationService authApplicationService;

  /**
   * 查询系统已启用的登录方式。
   */
    @GetMapping("/enabled-types")
    public List<String> getEnabledAuthTypes() {
        return authConfigProperties.getEnabledTypes();
    }

  /**
   * 校验登录信息并完成用户登录。
   */
  @PostMapping("/login")
    public LoginVO login(@RequestBody @Valid LoginRequest request, HttpServletRequest servletRequest) {
        return authApplicationService.login(request, servletRequest);
    }

    /**
     * 退出当前用户登录。
     */
    @PostMapping("/logout")
    public void logout() {
        StpUtil.logout();
    }
}

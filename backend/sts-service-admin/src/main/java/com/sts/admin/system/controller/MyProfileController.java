package com.sts.admin.system.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.sts.admin.aop.annotation.*;
import com.sts.admin.system.dto.*;
import com.sts.admin.system.service.MyProfileService;
import com.sts.admin.system.vo.MyProfileVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 本人资料和改密仅要求登录，不复用管理员编辑用户权限。 */
@RestController @RequestMapping("/system/users") @SaCheckLogin @RequiredArgsConstructor
public class MyProfileController {
  /** 本人专用服务，拒绝授权字段进入写入。 */
  private final MyProfileService profiles;
  /** 返回真实可编辑资料。 */
  @GetMapping("/my-profile") public MyProfileVO profile() { return profiles.read(); }
  /** 更新本人允许字段。 */
  @PutMapping("/my-profile")
  @OperLog(title = "本人资料", businessType = BusinessType.UPDATE, saveRequestData = false, saveResponseData = false)
  public void update(@RequestBody @Valid MyProfileRequest request) { profiles.update(request); }
  /** 密码及摘要不进入操作日志。 */
  @PutMapping("/my-password")
  @OperLog(title = "本人改密", businessType = BusinessType.UPDATE, saveRequestData = false, saveResponseData = false)
  public void password(@RequestBody @Valid MyPasswordRequest request) { profiles.changePassword(request); }
}

package com.sts.admin.system.dto;

import com.sts.admin.validation.NoInvisibleCharacters;
import jakarta.validation.constraints.*;
import lombok.Data;

/** 本人资料只允许三项业务字段；版本仅用于冲突保护。 */
@Data
public class MyProfileRequest {
  /** 当前姓名，不回写既有考生身份快照。 */
  @NotBlank @Size(max = 100) @NoInvisibleCharacters
  private String userName;
  /** 本人真实手机号；空值允许清除。 */
  @Size(max = 20) @NoInvisibleCharacters
  private String phone;
  /** 本人邮箱，沿用管理资料的格式校验。 */
  @Email @Size(max = 128) @NoInvisibleCharacters
  private String email;
  /** 用户当前乐观锁版本。 */
  @NotNull @Min(0)
  private Integer version;
}

package com.sts.admin.system.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.ToString;

/** 密码摘要仅用于写入，不序列化响应或出现在对象日志。 */
@Data @ToString(onlyExplicitlyIncluded = true)
public class MyPasswordRequest {
  /** 旧密码的SHA-256摘要。 */
  @NotBlank @Pattern(regexp = "^[a-f0-9]{64}$")
  @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
  private String oldPassword;
  /** 新密码的SHA-256摘要；确认密码只在前端比较。 */
  @NotBlank @Pattern(regexp = "^[a-f0-9]{64}$")
  @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
  private String newPassword;
}

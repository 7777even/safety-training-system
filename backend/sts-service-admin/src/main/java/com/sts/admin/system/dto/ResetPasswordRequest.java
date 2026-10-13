package com.sts.admin.system.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sts.admin.validation.NoInvisibleCharacters;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class ResetPasswordRequest {
    @NotBlank(message = "密码不能为空")
    @Pattern(regexp = "^[a-f0-9]{64}$", message = "密码必须为64位小写十六进制SHA-256摘要")
    @NoInvisibleCharacters(message = "密码不得包含不可见或控制字符")
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;
}

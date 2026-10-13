package com.sts.admin.system.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sts.admin.aop.sensitive.Sensitive;
import com.sts.admin.aop.sensitive.SensitiveStrategy;
import com.sts.admin.validation.NoInvisibleCharacters;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class UserCreateRequest {
    @NotBlank(message = "用户编码不能为空")
    @NoInvisibleCharacters(message = "用户编码不得包含不可见或控制字符")
    private String userCode;

    @NotBlank(message = "用户名称不能为空")
    @NoInvisibleCharacters(message = "用户名称不得包含不可见或控制字符")
    private String userName;

    @Sensitive(strategy = SensitiveStrategy.PHONE)
    @NoInvisibleCharacters(message = "手机号不得包含不可见或控制字符")
    private String phone;

    @NotNull(message = "部门不能为空")
    private Long deptId;

    @NotBlank(message = "密码不能为空")
    @Pattern(regexp = "^[a-f0-9]{64}$", message = "密码必须为64位小写十六进制SHA-256摘要")
    @NoInvisibleCharacters(message = "密码不得包含不可见或控制字符")
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

    private Integer status = 1;
    private List<Long> roleIds;

  /**
   * 成员类型：INTERNAL 内部员工 / EXTERNAL 外部人员 / REGULATOR 监管人员。
   */
  @Pattern(regexp = "^(INTERNAL|EXTERNAL|REGULATOR)$", message = "成员类型不合法")
  @NoInvisibleCharacters(message = "成员类型不得包含不可见或控制字符")
  private String memberType = "INTERNAL";

  @Email(message = "邮箱格式不正确")
  @Size(max = 128, message = "邮箱不能超过128个字符")
  @NoInvisibleCharacters(message = "邮箱不得包含不可见或控制字符")
  private String email;

  @Size(max = 100, message = "职务不能超过100个字符")
  @NoInvisibleCharacters(message = "职务不得包含不可见或控制字符")
  private String jobTitle;

  private LocalDate entryDate;

  /**
   * 标签 ID 列表；用于跨部门跨岗位圈定培训与考试对象。
   */
  private List<Long> tagIds;

  /**
   * 岗位 ID 列表。
   */
  private List<Long> positionIds;

  /**
   * 主岗位 ID，必须包含在 positionIds 中。
   */
  private Long primaryPositionId;

}

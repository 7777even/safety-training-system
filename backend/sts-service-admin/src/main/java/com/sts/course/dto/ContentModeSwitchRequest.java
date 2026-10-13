package com.sts.course.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class ContentModeSwitchRequest {
  @NotBlank(message = "目标内容模式不能为空")
  @Pattern(regexp = "SINGLE|MULTI_CHAPTER", message = "内容模式仅支持 SINGLE 或 MULTI_CHAPTER")
  private String contentMode;

  @NotNull(message = "版本号不能为空")
  private Integer version;
}

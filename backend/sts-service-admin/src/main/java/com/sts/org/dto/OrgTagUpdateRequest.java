package com.sts.org.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class OrgTagUpdateRequest {
  @NotBlank(message = "标签名称不能为空")
  private String tagName;

  private String description = "";

  @NotNull(message = "状态不能为空")
  private Integer status;

  @NotNull(message = "版本号不能为空")
  private Integer version;
}

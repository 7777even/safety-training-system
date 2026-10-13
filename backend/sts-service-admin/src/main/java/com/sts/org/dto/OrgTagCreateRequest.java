package com.sts.org.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class OrgTagCreateRequest {
  @NotNull(message = "机构ID不能为空")
  private Long organizationId;

  @NotBlank(message = "标签名称不能为空")
  private String tagName;

  private String description = "";
  private Integer status = 1;
}

package com.sts.org.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class OrgPositionUpdateRequest {
  @NotBlank(message = "岗位编码不能为空")
  private String positionCode;

  @NotBlank(message = "岗位名称不能为空")
  private String positionName;

  private String description = "";

  @NotNull(message = "状态不能为空")
  private Integer status;

  @NotNull(message = "版本号不能为空")
  private Integer version;
}

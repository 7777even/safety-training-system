package com.sts.org.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class OrgPositionCreateRequest {
  @NotNull(message = "机构ID不能为空")
  private Long organizationId;

  @NotBlank(message = "岗位编码不能为空")
  private String positionCode;

  @NotBlank(message = "岗位名称不能为空")
  private String positionName;

  private String description = "";
  private Integer status = 1;
}

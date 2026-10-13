package com.sts.org.dto;

import lombok.Data;

@Data
public class OrgPositionQuery {
  private Long organizationId;
  private String positionCode;
  private String positionName;
  private Integer status;
}

package com.sts.org.dto;

import lombok.Data;

@Data
public class OrgTagQuery {
  private Long organizationId;
  private String tagName;
  private Integer status;
}

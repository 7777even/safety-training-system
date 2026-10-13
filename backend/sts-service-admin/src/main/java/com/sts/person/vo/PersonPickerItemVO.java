package com.sts.person.vo;

import lombok.Data;

@Data
public class PersonPickerItemVO {
  private Long userId;
  private String userCode;
  private String userName;
  private Long organizationId;
  private Long deptId;
  private String deptName;
  private String memberType;
  private String jobTitle;
  private Integer status;
}

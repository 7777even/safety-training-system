package com.sts.person.dto;

import lombok.Data;

@Data
public class PersonPickerQuery {
  private String keyword;
  private Long deptId;
  /** 按岗位筛选当前成员。 */
  private Long positionId;
  /** 按条件组筛选当前成员。 */
  private Long groupId;
}

package com.sts.person.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class PersonSelectionRequest {
  @NotBlank(message = "选择类型不能为空")
  @Pattern(regexp = "ALL|DEPT|USER|POSITION|GROUP", message = "选择类型仅支持 ALL、DEPT、USER、POSITION 或 GROUP")
  /** 按本次操作展开名单，不建立任务与组的动态关系。 */
  private String selectionType;

  @NotNull(message = "选择对象列表不能为空")
  /** 所选部门、人员、岗位或条件组ID；全员时为空。 */
  @Size(max = 1000, message = "选择对象不能超过1000项")
  private List<@NotNull @Positive Long> subjectIds;
}

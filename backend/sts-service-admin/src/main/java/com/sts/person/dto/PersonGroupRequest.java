package com.sts.person.dto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
/** 条件用户组维护请求。 */
@Data public class PersonGroupRequest {
  /** 名称。 */
  @NotBlank @Size(max=100) private String groupName;
  /** 状态。 */
  @NotNull @Min(0) @Max(1) private Integer status;
  /** 当前成员计算条件。 */
  @Valid @NotNull private PersonClassificationCondition conditions;
  /** 编辑必须提供原版本。 */
  @Min(0) private Integer version;
}

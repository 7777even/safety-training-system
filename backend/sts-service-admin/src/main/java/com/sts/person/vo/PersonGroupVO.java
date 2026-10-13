package com.sts.person.vo;
import com.sts.person.dto.PersonClassificationCondition;
import lombok.Data;
/** 用户组展示，不泄露持久化JSON。 */
@Data public class PersonGroupVO {
  /** 用户组ID。 */
  private Long groupId;
  /** 名称。 */
  private String groupName;
  /** 状态。 */
  private Integer status;
  /** 版本。 */
  private Integer version;
  /** 固定筛选条件。 */
  private PersonClassificationCondition conditions;
}

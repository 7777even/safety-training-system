package com.sts.person.entity;
import com.baomidou.mybatisplus.annotation.*;
import com.sts.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
/** 用户组保存筛选条件，下发时读取当前有效成员。 */
@Data @EqualsAndHashCode(callSuper=true) @TableName("sts_person_group")
public class PersonGroup extends BaseEntity {
  /** 用户组主键。 */
  @TableId(type=IdType.AUTO) private Long groupId;
  /** 所属租户。 */
  private Long organizationId;
  /** 用户组名称。 */
  private String groupName;
  /** 0停用/1启用。 */
  private Integer status;
  /** 已校验的固定条件JSON。 */
  private String conditionsJson;
}

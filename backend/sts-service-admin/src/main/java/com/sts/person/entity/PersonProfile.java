package com.sts.person.entity;
import com.baomidou.mybatisplus.annotation.*;
import com.sts.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
/** 独立手工特征，不覆盖系统培训事实。 */
@Data @EqualsAndHashCode(callSuper = true) @TableName("sts_person_profile")
public class PersonProfile extends BaseEntity {
  /** 特征记录主键。 */
  @TableId(type=IdType.AUTO) private Long profileId;
  /** 当前租户。 */
  private Long organizationId;
  /** 所属人员。 */
  private Long userId;
  /** 出生日期；允许清空。 */
  @TableField(updateStrategy = FieldStrategy.ALWAYS)
  private java.time.LocalDate birthDate;
  /** 性别；允许清空。 */
  @TableField(updateStrategy = FieldStrategy.ALWAYS)
  private String gender;
  /** 职业；允许清空。 */
  @TableField(updateStrategy = FieldStrategy.ALWAYS)
  private String occupation;
  /** 学历；允许清空。 */
  @TableField(updateStrategy = FieldStrategy.ALWAYS)
  private String education;
  /** 专业；允许清空。 */
  @TableField(updateStrategy = FieldStrategy.ALWAYS)
  private String major;
  /** 从业经历；允许清空。 */
  @TableField(updateStrategy = FieldStrategy.ALWAYS)
  private String workExperience;
  /** 专长；允许清空。 */
  @TableField(updateStrategy = FieldStrategy.ALWAYS)
  private String specialty;
  /** 职业资格；允许清空。 */
  @TableField(updateStrategy = FieldStrategy.ALWAYS)
  private String qualification;
  /** 人工奖惩说明；允许清空。 */
  @TableField(updateStrategy = FieldStrategy.ALWAYS)
  private String rewardPenaltyNote;
  /** 工作内容；允许清空。 */
  @TableField(updateStrategy = FieldStrategy.ALWAYS)
  private String workContent;
  /** 工作技能；允许清空。 */
  @TableField(updateStrategy = FieldStrategy.ALWAYS)
  private String workSkills;
}

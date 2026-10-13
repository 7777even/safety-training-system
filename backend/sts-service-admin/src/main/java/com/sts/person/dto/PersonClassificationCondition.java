package com.sts.person.dto;
import lombok.Data;
import jakarta.validation.constraints.*;
/** 固定条件分类；缺失属性不匹配指定条件。 */
@Data public class PersonClassificationCondition {
  /** 部门并集；字段之间取交集。 */
  @Size(max=100) private java.util.List<@NotNull @Positive Long> deptIds;
  /** 岗位并集；字段之间取交集。 */
  @Size(max=100) private java.util.List<@NotNull @Positive Long> positionIds;
  /** 标签并集；字段之间取交集。 */
  @Size(max=100) private java.util.List<@NotNull @Positive Long> tagIds;
  /** 性别并集。 */
  @Size(max=100) private java.util.List<@NotBlank @Size(max=1000) String> genders;
  /** 学历并集。 */
  @Size(max=100) private java.util.List<@NotBlank @Size(max=1000) String> educations;
  /** 培训结果并集。 */
  @Size(max=100) private java.util.List<@NotBlank @Size(max=1000) String> resultStatuses;
  /** 年龄边界，按当前日期计算。 */
  @Min(0) @Max(150) private Integer minAge;
  /** 年龄边界，按当前日期计算。 */
  @Min(0) @Max(150) private Integer maxAge;
  /** 职业关键词。 */
  @Size(max=1000) private String occupation;
  /** 专业关键词。 */
  @Size(max=1000) private String major;
  /** 从业经历关键词。 */
  @Size(max=1000) private String workExperience;
  /** 专长关键词。 */
  @Size(max=1000) private String specialty;
  /** 职业资格关键词。 */
  @Size(max=1000) private String qualification;
  /** 人工奖惩说明关键词。 */
  @Size(max=1000) private String rewardPenaltyNote;
  /** 工作内容关键词。 */
  @Size(max=1000) private String workContent;
  /** 工作技能关键词。 */
  @Size(max=1000) private String workSkills;
  /** 工号/姓名关键词。 */
  @Size(max=1000) private String keyword;
  /** 培训计划事实筛选。 */
  @Positive private Long trainingPlanId;
}

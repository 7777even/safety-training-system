package com.sts.person.vo;
import lombok.Data;
/** 人员手工特征展示。 */
@Data public class PersonProfileVO {
  /** 出生日期。 */
  @jakarta.validation.constraints.PastOrPresent(message="出生日期不能晚于今天")
  private java.time.LocalDate birthDate;
  /** 性别。 */
  @jakarta.validation.constraints.Size(max=1000)
  @jakarta.validation.constraints.Pattern(regexp="MALE|FEMALE|OTHER", message="性别无效")
  private String gender;
  /** 职业。 */
  @jakarta.validation.constraints.Size(max=1000)
  private String occupation;
  /** 学历。 */
  @jakarta.validation.constraints.Size(max=1000)
  private String education;
  /** 专业。 */
  @jakarta.validation.constraints.Size(max=1000)
  private String major;
  /** 从业经历。 */
  @jakarta.validation.constraints.Size(max=1000)
  private String workExperience;
  /** 专长。 */
  @jakarta.validation.constraints.Size(max=1000)
  private String specialty;
  /** 职业资格。 */
  @jakarta.validation.constraints.Size(max=1000)
  private String qualification;
  /** 人工奖惩说明。 */
  @jakarta.validation.constraints.Size(max=1000)
  private String rewardPenaltyNote;
  /** 工作内容。 */
  @jakarta.validation.constraints.Size(max=1000)
  private String workContent;
  /** 工作技能。 */
  @jakarta.validation.constraints.Size(max=1000)
  private String workSkills;
  /** 版本，首次保存为0。 */
  private Integer version;
  /** 人员ID。 */
  private Long userId;
}

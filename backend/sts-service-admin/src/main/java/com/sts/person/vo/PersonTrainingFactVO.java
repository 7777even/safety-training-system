package com.sts.person.vo;
import lombok.Data;
/** 只读培训事实，不能通过人员特征接口修改。 */
@Data public class PersonTrainingFactVO {
  /** 内部读取正式结果所需的参与ID，不加入对外展示字段。 */
  @com.fasterxml.jackson.annotation.JsonIgnore
  private Long trainingParticipantId;
  /** 培训计划ID。 */
  private Long trainingPlanId;
  /** 计划名称。 */
  private String planName;
  /** 培训结果。 */
  private String resultStatus;
  /** 完成时间。 */
  private java.time.LocalDateTime completedTime;
  /** 线上学时。 */
  private java.math.BigDecimal onlineHours;
  /** 线下学时。 */
  private java.math.BigDecimal offlineHours;
  /** 已登记培训奖惩标题。 */
  private String rewardPenaltyTitles;
}

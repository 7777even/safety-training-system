package com.sts.question.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.sts.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 独立于正式考核和成绩投影的个人练习持久化记录。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sts_learner_practice_session")
public class LearnerPracticeSession extends BaseEntity {
  /** 记录主键。 */
  @TableId(type = IdType.AUTO)
  private Long sessionId;
  /** 所属机构。 */
  private Long organizationId;
  /** 当前学员。 */
  private Long userId;
  /** 已分配练习人员引用。 */
  private Long candidateId;
  /** 错题再练引用。 */
  private Long sourceQuestionId;
  /** PRACTICE或SIMULATION。 */
  private String mode;
  /** 题源名称快照。 */
  private String title;
  /** ANSWERING、SUBMITTED或CLEARED。 */
  private String status;
  /** 本人启动幂等令牌。 */
  private String clientToken;
  /** 开始时间。 */
  private java.time.LocalDateTime startedTime;
  /** 交卷时间。 */
  private java.time.LocalDateTime submittedTime;
}

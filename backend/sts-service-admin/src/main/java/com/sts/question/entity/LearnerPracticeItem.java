package com.sts.question.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.sts.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 独立于正式考核和成绩投影的个人练习持久化记录。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sts_learner_practice_item")
public class LearnerPracticeItem extends BaseEntity {
  /** 记录主键。 */
  @TableId(type = IdType.AUTO)
  private Long itemId;
  /** 所属机构。 */
  private Long organizationId;
  /** 个人练习会话。 */
  private Long sessionId;
  /** 原题目。 */
  private Long questionId;
  /** 冻结版本。 */
  private Long questionVersionId;
  /** 含服务端答案的冻结题目。 */
  private String snapshotJson;
  /** 本人答案。 */
  private String answerJson;
  /** 是否已作答。 */
  private Integer answered;
  /** AUTO_CORRECT、AUTO_WRONG或NOT_EVALUATED。 */
  private String evaluationResult;
  /** 会话内题序。 */
  private Integer sortOrder;
}

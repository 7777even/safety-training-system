package com.sts.training.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sts.common.entity.BaseAuditEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 培训计划发布时冻结的一条题目版本候选。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sts_training_plan_question_pool")
public class TrainingPlanQuestionPool extends BaseAuditEntity {
  @TableId(type = IdType.AUTO)
  private Long planQuestionPoolId;
  private Long organizationId;
  private Long trainingPlanId;
  private Long planContentId;
  private Long questionBankId;
  private Long questionId;
  private Long questionVersionId;
  private String questionType;
  private String difficulty;
  private String knowledgePointIdsJson;
  private String evaluationMode;
  private Integer candidateOrder;
  private String poolHash;
}

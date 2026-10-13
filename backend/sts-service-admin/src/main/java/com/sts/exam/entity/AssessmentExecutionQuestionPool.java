package com.sts.exam.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sts.common.entity.BaseAuditEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 考核执行从培训发布快照复制的一条不可变题目候选。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sts_assessment_execution_question_pool")
public class AssessmentExecutionQuestionPool extends BaseAuditEntity {
  @TableId(type = IdType.AUTO)
  private Long executionQuestionPoolId;
  private Long organizationId;
  private Long assessmentExecutionId;
  private Long sourcePlanContentId;
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

package com.sts.question.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.sts.common.entity.BaseAuditEntity;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sts_question_bank_knowledge")
public class QuestionBankKnowledge extends BaseAuditEntity {
  @TableId(type = IdType.AUTO)
  private Long bankKnowledgeId;
  private Long questionBankId;
  private Long knowledgePointId;
}

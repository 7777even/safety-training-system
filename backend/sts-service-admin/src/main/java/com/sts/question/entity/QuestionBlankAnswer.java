package com.sts.question.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.sts.common.entity.BaseAuditEntity;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sts_question_blank_answer")
public class QuestionBlankAnswer extends BaseAuditEntity {
  @TableId(type = IdType.AUTO)
  private Long blankAnswerId;
  private Long questionVersionId;
  private Integer blankNo;
  private Integer answerGroupNo;
  private String answerText;
  private Integer sortOrder;
}

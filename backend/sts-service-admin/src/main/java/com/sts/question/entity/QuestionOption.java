package com.sts.question.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.sts.common.entity.BaseAuditEntity;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sts_question_option")
public class QuestionOption extends BaseAuditEntity {
  @TableId(type = IdType.AUTO)
  private Long questionOptionId;
  private Long questionVersionId;
  private String optionKey;
  private String optionContent;
  private Integer isCorrect;
  private Integer sortOrder;
}

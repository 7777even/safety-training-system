package com.sts.question.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.sts.common.entity.BaseEntity;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sts_question_question")
public class Question extends BaseEntity {
  @TableId(type = IdType.AUTO)
  private Long questionId;
  private Long organizationId;
  private Long questionBankId;
  private String questionCode;
  private Integer currentVersionNo;
  private String archiveStatus;
  private Integer status;
}

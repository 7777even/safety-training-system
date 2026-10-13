package com.sts.question.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.sts.common.entity.BaseEntity;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sts_question_bank")
public class QuestionBank extends BaseEntity {
  @TableId(type = IdType.AUTO)
  private Long questionBankId;
  private Long organizationId;
  private String bankCode;
  private String bankName;
  private Long bankCategoryId;
  private String difficulty;
  private String questionOrderMode;
  private String archiveStatus;
  private Integer status;
}

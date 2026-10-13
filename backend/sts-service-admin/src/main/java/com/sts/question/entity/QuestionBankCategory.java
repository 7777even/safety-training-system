package com.sts.question.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.sts.common.entity.BaseEntity;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sts_question_bank_category")
public class QuestionBankCategory extends BaseEntity {
  @TableId(type = IdType.AUTO)
  private Long bankCategoryId;
  private Long organizationId;
  private String categoryName;
  private String description;
  private Integer sortOrder;
  private Integer status;
}

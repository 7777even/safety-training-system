package com.sts.evaluation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sts.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sts_evaluation_form")
public class EvaluationForm extends BaseEntity {
  @TableId(type = IdType.AUTO)
  private Long evaluationFormId;
  private Long organizationId;
  private String formCode;
  private String formName;
  private String formType;
  private Integer currentVersionNo;
  private Integer status;
  private String description;
}

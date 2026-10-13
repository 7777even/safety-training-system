package com.sts.evaluation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sts.common.entity.BaseAuditEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sts_evaluation_item")
public class EvaluationItem extends BaseAuditEntity {
  @TableId(type = IdType.AUTO)
  private Long evaluationItemId;
  private Long formVersionId;
  private String itemCode;
  private String itemName;
  private String itemType;
  private BigDecimal maxScore;
  private BigDecimal weight;
  private Integer sortOrder;
  private Integer isRequired;
}

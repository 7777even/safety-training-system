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
@TableName("sts_evaluation_score_rule")
public class EvaluationScoreRule extends BaseAuditEntity {
  @TableId(type = IdType.AUTO)
  private Long scoreRuleId;
  private Long formVersionId;
  private Long evaluationItemId;
  private String calculationType;
  private String sourceType;
  private BigDecimal scoreChange;
  private BigDecimal weight;
  private Integer sortOrder;
  private String ruleDescription;
}

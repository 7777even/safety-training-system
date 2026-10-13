package com.sts.paper.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.sts.common.entity.BaseAuditEntity;
import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sts_paper_draw_rule")
public class PaperDrawRule extends BaseAuditEntity {
  @TableId(type = IdType.AUTO)
  private Long drawRuleId;
  private Long paperVersionId;
  private Long questionBankId;
  private String questionType;
  private String difficulty;
  private Long knowledgePointId;
  private Integer drawCount;
  private BigDecimal scorePerQuestion;
  private BigDecimal bankWeight;
  private Integer sortOrder;
}

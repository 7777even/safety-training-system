package com.sts.paper.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 随机试卷版本的抽题规则。与 randomSeed 一起构成抽题结果可重现的依据。
 */
@Data
public class PaperDrawRuleVO {
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

package com.sts.training.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 题库练习抽题规则。
 */
@Data
public class TrainingQuestionBankDrawRuleRequest {
  private String questionType;
  private String difficulty;
  private Long knowledgePointId;
  private Integer drawCount;
  /**
   * 历史兼容字段；新题库练习不评分。
   */
  @Deprecated
  private BigDecimal scorePerQuestion;
  private Integer sortOrder;
}

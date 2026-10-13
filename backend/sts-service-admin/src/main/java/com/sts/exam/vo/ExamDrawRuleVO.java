package com.sts.exam.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 题库练习抽题规则回读结构；仅在考试类型为 BANK_PRACTICE 时返回。
 */
@Data
public class ExamDrawRuleVO {
  /** 题型，与 question OpenAPI 的 questionType 枚举一致。 */
  private String questionType;
  /** 难度；为空表示不限难度。 */
  private String difficulty;
  /** 知识点 ID；为空表示不限知识点。 */
  private Long knowledgePointId;
  /** 抽取题数。 */
  private Integer drawCount;
  /** 每题分值。 */
  private BigDecimal scorePerQuestion;
  /** 抽题顺序，用于管理端稳定回显。 */
  private Integer sortOrder;
}

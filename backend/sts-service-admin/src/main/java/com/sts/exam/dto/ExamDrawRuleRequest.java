package com.sts.exam.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 题库练习抽题规则。
 * <p>
 * 结构与培训侧 {@code drawRules} 保持一致，抽题算法复用 {@code QuestionBankDrawEngine}；
 * 仅独立题库练习（{@code examType=BANK_PRACTICE} 且不属于培训计划）使用。
 */
@Data
public class ExamDrawRuleRequest {

  /** 抽取的题型：SINGLE 单选；MULTIPLE 多选；JUDGE 判断；FILL 填空；ESSAY 问答。 */
  @NotBlank(message = "抽题题型不能为空")
  private String questionType;

  /** 抽取的难度：ALL 不限；EASY 简单；NORMAL 普通；HARD 困难；VERY_HARD 很难。 */
  private String difficulty;

  /** 限定知识点 ID；为空表示不限知识点。 */
  private Long knowledgePointId;

  /** 抽取题目数量。 */
  @NotNull(message = "抽题数量不能为空")
  @Min(value = 1, message = "抽题数量必须大于 0")
  private Integer drawCount;

  /** 每题分值。 */
  private BigDecimal scorePerQuestion;

  /** 排序值，数值越小越靠前。 */
  private Integer sortOrder;
}

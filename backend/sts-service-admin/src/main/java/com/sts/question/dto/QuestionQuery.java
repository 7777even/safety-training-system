package com.sts.question.dto;

import lombok.Data;

@Data
public class QuestionQuery {
  private Long questionBankId;
  private String questionCode;
  private String archiveStatus;
  /**
   * 按题目当前是否存在该发布状态的版本筛选，如 PUBLISHED/DRAFT。
   */
  private String publishedStatus;
  private Integer status;
}

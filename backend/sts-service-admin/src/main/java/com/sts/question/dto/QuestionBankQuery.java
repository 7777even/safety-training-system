package com.sts.question.dto;

import lombok.Data;

@Data
public class QuestionBankQuery {
  private Long bankCategoryId;
  private String bankName;
  private String archiveStatus;
  private Integer status;
}

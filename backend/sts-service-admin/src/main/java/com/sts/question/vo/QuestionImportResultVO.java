package com.sts.question.vo;

import lombok.Data;

@Data
public class QuestionImportResultVO {
  private Long importBatchId;
  private String importStatus;
  private Integer totalRows;
  private Integer successRows;
  private Integer failureRows;
}

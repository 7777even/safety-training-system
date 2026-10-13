package com.sts.exam.vo;

import lombok.Data;

@Data
public class ExamAttemptOptionVO {
  private Long attemptOptionId;
  private String optionKey;
  private String optionContentSnapshot;
  private Integer isCorrectSnapshot;
  private Integer sortOrder;
}

package com.sts.question.vo;

import lombok.Data;

@Data
public class QuestionOptionVO {
  private Long questionOptionId;
  private String optionKey;
  private String optionContent;
  private Integer isCorrect;
  private Integer sortOrder;
}

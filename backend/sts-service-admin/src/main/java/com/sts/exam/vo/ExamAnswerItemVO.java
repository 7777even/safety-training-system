package com.sts.exam.vo;

import lombok.Data;

/**
 * 已暂存答案的结构化答案项。
 */
@Data
public class ExamAnswerItemVO {
  private Long answerItemId;
  private String itemType;
  private String itemKey;
  private String itemValue;
  private Integer sortOrder;
}

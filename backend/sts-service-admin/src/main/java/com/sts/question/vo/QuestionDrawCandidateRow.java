package com.sts.question.vo;

import lombok.Data;

/**
 * 公共候选池查询的扁平行；同一题目的多个知识点会返回多行，由服务层聚合。
 */
@Data
public class QuestionDrawCandidateRow {
  private Long questionId;
  private Long questionVersionId;
  private String questionType;
  private String difficulty;
  private String gradingMode;
  private Long knowledgePointId;
  private Integer correctAnswerCount;
  private Integer blankAnswerCount;
}

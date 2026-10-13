package com.sts.training.port;

import lombok.Data;

import java.util.List;

/**
 * 培训域向考试域传递的冻结题目版本，不包含题干和答案。
 */
@Data
public class TrainingQuestionPoolItem {
  private Long questionId;
  private Long questionVersionId;
  private String questionType;
  private String difficulty;
  private List<Long> knowledgePointIds;
  private String evaluationMode;
  private Integer candidateOrder;
}

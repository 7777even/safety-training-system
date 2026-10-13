package com.sts.exam.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class ExamAttemptQuestionVO {
  private Long attemptQuestionId;
  private Long questionId;
  private Long questionVersionId;
  private String questionType;
  private String stemSnapshot;
  /** 填空题的安全空位数量；仅表达需填写的空位，不包含任何正确答案。 */
  private Integer blankCount;
  private String analysisSnapshot;
  private String correctAnswerSnapshotJson;
  private String standardAnswer;
  private String gradingMode;
  private BigDecimal score;
  private Integer sortOrder;
  private Integer drawRuleIndex;
  private String evaluationMode;
  private List<ExamAttemptOptionVO> options;
  private ExamAnswerVO answer;
}

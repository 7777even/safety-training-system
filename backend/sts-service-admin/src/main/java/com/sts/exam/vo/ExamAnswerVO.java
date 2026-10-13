package com.sts.exam.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 作答答案及其评分结果；学员视图会按成绩与答案详情策略裁剪评分字段。
 */
@Data
public class ExamAnswerVO {
  private Long examAnswerId;
  private Long examAttemptId;
  private Long attemptQuestionId;
  private String answerText;
  private List<ExamAnswerItemVO> items;
  private Integer isAnswered;
  private String gradingStatus;
  private String evaluationResult;
  private LocalDateTime answeredTime;
  private LocalDateTime lastSavedTime;
  private BigDecimal autoScore;
  private BigDecimal manualScore;
  /** 仅管理端作答详情返回的最近一次批阅意见，学员读取时由服务端裁剪。 */
  private String reviewComment;
  private BigDecimal finalScore;
  private Integer isCorrect;
}

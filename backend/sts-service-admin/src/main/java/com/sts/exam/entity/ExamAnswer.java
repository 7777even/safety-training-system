package com.sts.exam.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sts.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sts_exam_answer")
public class ExamAnswer extends BaseEntity {
  @TableId(type = IdType.AUTO)
  private Long examAnswerId;
  private Long organizationId;
  private Long examAttemptId;
  private Long attemptQuestionId;
  private String answerText;
  private Integer isAnswered;
  private BigDecimal autoScore;
  private BigDecimal manualScore;
  private BigDecimal finalScore;
  private Integer isCorrect;
  private String gradingStatus;
  /**
   * AUTO_CORRECT/AUTO_WRONG/NOT_EVALUATED，历史答案为空。
   */
  private String evaluationResult;
  private LocalDateTime answeredTime;
  private LocalDateTime lastSavedTime;
}

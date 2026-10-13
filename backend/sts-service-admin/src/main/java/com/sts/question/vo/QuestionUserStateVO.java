package com.sts.question.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class QuestionUserStateVO {
  private Long userQuestionStateId;
  private Long organizationId;
  private Long userId;
  private Long questionId;
  private Integer isFavorite;
  private Integer wrongCount;
  private Integer correctCount;
  private LocalDateTime lastAnswerTime;
  private LocalDateTime lastWrongTime;
  private Long lastExamAttemptId;
  private Integer version;
}

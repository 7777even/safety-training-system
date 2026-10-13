package com.sts.exam.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 成绩发布流水。每次发布追加一条，用于说明成绩由谁在何时发布为何值。
 */
@Data
public class ExamScorePublishLogVO {
  private Long scorePublishLogId;
  private Long examId;
  private Long examAttemptId;
  private Long publishedBy;
  private LocalDateTime publishedTime;
  private String publishType;
  private BigDecimal scoreSnapshot;
  private String resultSnapshot;
}

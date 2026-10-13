package com.sts.training.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 培训内容执行配置。字段按 contentType 使用，未使用字段必须为空。
 */
@Data
public class TrainingContentExecutionConfig {
  /**
   * COURSE：完成规则（ALL_REQUIRED_ITEMS/ALL_ITEMS）。
   */
  private String completionRule;

  /**
   * QUESTION_BANK：练习时长秒数。
   */
  private Integer durationSeconds;
  /**
   * OFFLINE：及格分数。QUESTION_BANK 仅保留历史兼容，新练习不使用。
   */
  private BigDecimal passScore;
  /**
   * QUESTION_BANK：最大作答次数。
   */
  private Integer maxAttempts;
  /**
   * QUESTION_BANK：抽题规则。
   */
  private List<TrainingQuestionBankDrawRuleRequest> drawRules;

  /**
   * OFFLINE：场次教师用户 ID。
   */
  private Long teacherUserId;
  /**
   * OFFLINE：地点。
   */
  private String location;
  /**
   * OFFLINE：开始时间。
   */
  private LocalDateTime startTime;
  /**
   * OFFLINE：结束时间。
   */
  private LocalDateTime endTime;
}

package com.sts.training.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 学员从培训统一入口进入内容后的跳转信息。
 */
@Data
public class TrainingLearnerEnterVO {
  /**
   * 计划内容类型。
   */
  private String contentType;
  /**
   * COURSE：本人课程报名 ID。
   */
  private Long courseEnrollmentId;
  /**
   * EXAM/QUESTION_BANK：创建或恢复的作答尝试 ID。
   */
  private Long examAttemptId;
  /**
   * OFFLINE：线下场次 ID。
   */
  private Long offlineSessionId;
  /**
   * OFFLINE：场次名称。
   */
  private String sessionName;
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

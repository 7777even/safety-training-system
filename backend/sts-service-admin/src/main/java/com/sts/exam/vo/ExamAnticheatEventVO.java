package com.sts.exam.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 作答过程中的防作弊事件。事件只追加，用于事后追溯。
 */
@Data
public class ExamAnticheatEventVO {
  private Long anticheatEventId;
  private Long examAttemptId;
  private String eventType;
  private LocalDateTime eventTime;
  private Integer eventCount;
  private String actionTaken;
  private Long evidenceFileId;
  private String clientPayloadJson;
}

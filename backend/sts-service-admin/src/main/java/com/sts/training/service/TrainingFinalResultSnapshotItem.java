package com.sts.training.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;

/**
 * 最终结果快照中的逐内容来源项。字段顺序固定。
 */
@Data
@JsonInclude(JsonInclude.Include.ALWAYS)
@JsonPropertyOrder({
  "planContentId", "contentType", "executionId", "sourceRecordType", "sourceRecordId",
  "finalAttemptId", "evaluatedCount", "correctCount", "wrongCount", "unassessedCount", "accuracyRate",
  "sourceStatus", "resultStatus", "effectiveDurationSeconds", "score", "completedTime"
})
public class TrainingFinalResultSnapshotItem {
  private Long planContentId;
  private String contentType;
  private Long executionId;
  private String sourceRecordType;
  private Long sourceRecordId;
  private Long finalAttemptId;
  private Integer evaluatedCount;
  private Integer correctCount;
  private Integer wrongCount;
  private Integer unassessedCount;
  private String accuracyRate;
  private String sourceStatus;
  private String resultStatus;
  private Integer effectiveDurationSeconds;
  private String score;
  private String completedTime;
}

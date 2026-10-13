package com.sts.training.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;

import java.util.List;

/**
 * 计划结束时写入参训人的规范化最终结果快照。字段顺序固定，供 hash。
 */
@Data
@JsonInclude(JsonInclude.Include.ALWAYS)
@JsonPropertyOrder({
  "schemaVersion", "trainingPlanId", "trainingParticipantId", "resultStatus", "finalizedTime",
  "onlineHours", "offlineHours", "courseScore", "examScore", "offlineScore", "compositeScore",
  "officialCalculationRunId", "items"
})
public class TrainingFinalResultSnapshot {
  private int schemaVersion = 3;
  private Long trainingPlanId;
  private Long trainingParticipantId;
  private String resultStatus;
  private String finalizedTime;
  private String onlineHours;
  private String offlineHours;
  private String courseScore;
  private String examScore;
  private String offlineScore;
  private String compositeScore;
  private Long officialCalculationRunId;
  private List<TrainingFinalResultSnapshotItem> items;
}

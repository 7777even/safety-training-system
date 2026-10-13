package com.sts.training.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;

/**
 * 计划专属考试快照中的批阅人。字段顺序固定，供规范化 hash。
 */
@Data
@JsonInclude(JsonInclude.Include.ALWAYS)
@JsonPropertyOrder({"reviewerUserId", "assignmentSource"})
public class TrainingExamReviewerSnapshot {
  private Long reviewerUserId;
  private String assignmentSource;
}

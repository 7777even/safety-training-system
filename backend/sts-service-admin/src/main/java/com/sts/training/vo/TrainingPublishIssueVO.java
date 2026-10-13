package com.sts.training.vo;

import lombok.Data;

/**
 * 培训计划发布预检或发布失败的单条阻塞项。
 */
@Data
public class TrainingPublishIssueVO {
  private Integer code;
  private String message;
  private Long planContentId;
  private String contentType;
  private Long stageId;
  private String stageName;
  private String contentName;
  private Long resourceId;
  private String resourceName;
  private Integer ruleIndex;
  private String questionType;
  private String difficulty;
  private Long knowledgePointId;
  private Integer requiredCount;
  private Integer availableCount;
  private String suggestion;
}

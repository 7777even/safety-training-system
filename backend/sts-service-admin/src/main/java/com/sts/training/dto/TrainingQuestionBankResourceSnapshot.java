package com.sts.training.dto;

import com.sts.training.port.TrainingQuestionPoolItem;
import lombok.Data;

import java.util.List;

/**
 * 题库练习发布快照 v2，审计保存规范化候选版本清单及其指纹。
 */
@Data
public class TrainingQuestionBankResourceSnapshot {
  private Integer schemaVersion;
  private String contentType;
  private Long resourceId;
  private String bankCode;
  private String bankName;
  private TrainingContentExecutionConfig executionConfig;
  private String poolHash;
  private Integer candidateCount;
  private List<TrainingQuestionPoolItem> candidates;
}

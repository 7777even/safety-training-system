package com.sts.evaluation.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 计划课程对应的专家查课提交状态，用于执行工作台只读展示。 */
@Data
public class ExpertInspectionSummaryVO {
  private Long planContentId;
  private String contentName;
  private Long formVersionId;
  private Integer versionNo;
  private Long evaluationRecordId;
  private String recordStatus;
  private Long evaluatorUserId;
  private String evaluatorName;
  private LocalDateTime submittedTime;
  private BigDecimal totalScore;
  private String summary;
}

package com.sts.training.vo;

import com.sts.training.dto.TrainingContentExecutionConfig;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class TrainingPlanContentVO {
  private Long planContentId;
  private Long planStageId;
  private String contentType;
  private Long resourceId;
  private Long resourceVersionId;
  private String assessmentMode;
  private TrainingContentExecutionConfig executionConfig;
  private String resourceSnapshotJson;
  private String resourceSnapshotHash;
  private String contentName;
  private Integer isRequired;
  private Integer isScored;
  private BigDecimal scoreWeight;
  private String scoreAggregationMode;
  private BigDecimal requiredHours;
  private Integer sortOrder;
  private String contentStatus;
}

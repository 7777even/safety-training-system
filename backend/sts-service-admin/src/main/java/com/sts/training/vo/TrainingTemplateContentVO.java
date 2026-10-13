package com.sts.training.vo;

import com.sts.training.dto.TrainingContentExecutionConfig;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class TrainingTemplateContentVO {
  private Long templateContentId;
  private Long templateStageId;
  private String contentType;
  private Long resourceId;
  private Long resourceVersionId;
  private String assessmentMode;
  private TrainingContentExecutionConfig executionConfig;
  private String contentName;
  private Integer isRequired;
  private BigDecimal requiredHours;
  private Integer sortOrder;
  private Integer status;
}

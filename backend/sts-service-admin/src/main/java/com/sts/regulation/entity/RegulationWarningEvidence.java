package com.sts.regulation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 【内部保留】学时对比证据，不提供独立页面或对外操作。
 */
@Data
@TableName("sts_regulation_warning_evidence")
public class RegulationWarningEvidence {
  @TableId(type = IdType.AUTO)
  private Long warningEvidenceId;
  private Long warningId;
  private String evidenceKey;
  private String metricCode;
  private BigDecimal actualValue;
  private BigDecimal thresholdValue;
  private String unit;
  private String sourceType;
  private Long sourceId;
  private String evidenceSnapshotJson;
  private Long createBy;
  private LocalDateTime createTime;
}

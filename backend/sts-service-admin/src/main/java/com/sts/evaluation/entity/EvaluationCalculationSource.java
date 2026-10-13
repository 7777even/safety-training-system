package com.sts.evaluation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("sts_evaluation_calculation_source")
public class EvaluationCalculationSource {
  @TableId(type = IdType.AUTO)
  private Long calculationSourceId;
  private Long calculationRunId;
  private String sourceType;
  private Long planContentId;
  private String sourceRecordType;
  private Long sourceRecordId;
  private BigDecimal sourceScore;
  private BigDecimal normalizedScore;
  private BigDecimal weight;
  private String aggregationMode;
  private BigDecimal intermediateScore;
  private String sourceSnapshotJson;
  private Long createBy;
  private LocalDateTime createTime;
}

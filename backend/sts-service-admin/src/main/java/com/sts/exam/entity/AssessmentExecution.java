package com.sts.exam.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sts.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sts_assessment_execution")
public class AssessmentExecution extends BaseEntity {
  @TableId(type = IdType.AUTO)
  private Long assessmentExecutionId;
  private Long organizationId;
  private String sourceType;
  private Long sourceBusinessId;
  private Long examId;
  private Long courseOfferingId;
  private Long courseVersionId;
  private Long courseItemId;
  private String assessmentKind;
  private String resourceType;
  private Long resourceId;
  /**
   * 完整快照的结构版本，用于拒绝未知语义。
   */
  private Integer snapshotSchemaVersion;
  /**
   * 来源、资源和执行规则的唯一冻结事实。
   */
  private String snapshotJson;
  /**
   * 规范化完整快照的 SHA-256 摘要。
   */
  private String snapshotHash;
  /**
   * 以下字段由统一快照 Codec 校验后回填，不再映射为数据库列。
   */
  @TableField(exist = false)
  private String resourceNameSnapshot;
  @TableField(exist = false)
  private Integer durationSecondsSnapshot;
  @TableField(exist = false)
  private BigDecimal totalScoreSnapshot;
  @TableField(exist = false)
  private BigDecimal passScoreSnapshot;
  @TableField(exist = false)
  private String questionOrderModeSnapshot;
  @TableField(exist = false)
  private String multiSelectScoringSnapshot;
  @TableField(exist = false)
  private String policySnapshotJson;
  private String executionStatus;
}
